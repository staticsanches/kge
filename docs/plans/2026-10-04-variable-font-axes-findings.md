# Variable font axes — findings

**Date:** 2026-10-04.
**Status:** analysis only; this document decides nothing and changes no source.

Question: is it technically viable, with the current dependencies and wrappers, to
**enumerate** and **apply** OpenType variation axes consistently on JVM, Kotlin/JS
and Kotlin/WasmJS?

Convention: **[F]** fact read from a source (repo `path:line`, upstream file/URL,
or package file/line); **[M]** fact measured by running a throwaway probe during
this research; **[J]** judgement. Nothing in this document is a decision.

## 1. Executive summary

**[M]** The capability exists on every target, and it was exercised end to end on
the JVM today:

- `FT_Library_Version` on the LWJGL natives is **2.14.3** — the exact FreeType
  version the web package ships.
- `FT_Get_MM_Var` on `Roboto[wdth,wght].ttf` returns 2 axes with tags, min/default/
  max (`wght` 100/400/900, `wdth` 75/100/100) and resolved names (`Weight`,
  `Width`), plus 18 named styles.
- `FT_Set_Var_Design_Coordinates(wght=900)` changes the rendered ink of `A` at
  16 px from `9983` to `16978`; `wdth=75` at `wght=400` gives `9141`;
  `FT_Get_Var_Design_Coordinates` reads the values back exactly.
- `hb_font_set_variation(font, 'wght', 900)` changes the shaped advance of `A`
  from `668` to `698` (26.6).
- `FT_Done_MM_Var` and a `num_coords = 0` reset both return success.

**[M]** On the web the same package line was exercised in Node against the same
font: `ft.module._FT_Get_MM_Var` enumerates the identical axes, and
`_FT_Set_Var_Design_Coordinates(wght=900)` moves `horiAdvance` from `1336` to
`1395`. `harfbuzzjs`'s wrapped `Font.setVariations([new Variation("wght", 900)])`
also changes shaping.

**[F]** The capability was missing exactly one link: **no Kotlin code had ever
called an unwrapped Emscripten export**, and Kotlin's own interop documentation
says nothing about Emscripten `ccall`/`cwrap`/`HEAP*` (`wasm-js-interop.html`
covers only generic externals, `JsAny`/`JsReference` and typed arrays). The web
raster path **requires** that link, because `@zkl2333/freetype-wasm` wraps no
variation function at all.

**Classification: `SUPPORT NOW`.** The spike was run; it passed on `js` and
`wasmJs` and the link exists (§8). §9 gives the touch-point recommendation.

Two consequences that shape the design regardless of the spike's outcome:

1. **[F]** Shaping and rasterization keep **independent** coordinate stores on
   both platforms, and the automatic synchronisation HarfBuzz offers
   (`hb_ft_hb_font_changed`, automatic since HarfBuzz 11.0.0) is **unavailable**:
   LWJGL 3.4.3's bundled HarfBuzz natives do not export any `hb_ft_*` symbol, and
   `harfbuzzjs` is an `HB_TINY` build with no `hb_ft_*` at all. The engine must
   therefore push one coordinate set into both engines itself (§5).
2. **[M]** FreeType re-derives the size after a coordinate change, so the
   existing `NativeFace.ftSizePx` cache (`NativeFaceJvm.kt:26`, `:99-103`;
   `NativeFaceWeb.kt:26`, `:81-84`) does **not** need a variation-aware
   invalidation. Measured: with `FT_Set_Pixel_Sizes(16)` issued once and never
   re-issued, `wght` 400/900 read back `9983`/`16978` correctly in sequence.

## 2. Exact dependency versions

| Layer | Version | How read |
|---|---|---|
| Kotlin | 2.4.10 | `gradle/libs.versions.toml:2` |
| LWJGL (core/freetype/harfbuzz, BOM) | 3.4.3 | `gradle/libs.versions.toml:11`, `:26-32` |
| FreeType, JVM (LWJGL natives) | **2.14.3** | **[M]** `FT_Library_Version` |
| HarfBuzz, JVM (LWJGL natives) | 14.3.0 | **[F]** `strings libharfbuzz.dylib` → `harfbuzz ` + `14.3.0` |
| `harfbuzzjs` (npm) | 1.6.1 | `gradle/libs.versions.toml:13`; `kotlin-js-store/yarn.lock:986` |
| HarfBuzz inside the `harfbuzzjs` wasm | 14.4.0 | **[M]** wrapped `versionString()` |
| `@zkl2333/freetype-wasm` (npm) | 2.14.3 | `gradle/libs.versions.toml:14`; `kotlin-js-store/yarn.lock:264` |
| FreeType, web (wasm) | **2.14.3** | **[M]** `ft.version()` → `[2,14,3]` |
| `kotlinx-browser` / `kotlin-wrappers` | 0.5.0 / 2026.9.0 | `gradle/libs.versions.toml:9`, `:24-25` |
| Bundled fonts | Roboto 3.015, Roboto Mono 3.001 (variable) | `docs/plans/2026-09-17-font-bundle-findings.md`:86-104 |

**[F]** Both installs (`build/js/node_modules`, `build/wasm/node_modules`) are
byte-identical, so js and wasmJs run the same wasm. The npm lockfile pins
`harfbuzzjs@1.6.1` and `@zkl2333/freetype-wasm@2.14.3` by integrity hash
(`kotlin-js-store/yarn.lock:264-267`, `:986-989`; the wasm lock agrees).

**[F]** Upstream `harfbuzzjs` is already at 1.6.2; the variation API this research
relies on was added recently — `Variation` by PR
[#206](https://github.com/harfbuzz/harfbuzzjs/pull/206) (2026-05-06) and the full
`AxisInfo` (axisIndex/tag/nameId/flags) by PR
[#226](https://github.com/harfbuzz/harfbuzzjs/pull/226) (2026-08-08). The pinned
1.6.1 already carries both.

## 3. Capability matrix

### 3.1 What each target can do today

| Capability | JVM (LWJGL 3.4.3) | Web js / wasmJs |
|---|---|---|
| Enumerate axes (tag, min, def, max) | ✅ `FT_Get_MM_Var` (FreeType) **and** `OpenType.hb_ot_var_get_axis_infos` | ✅ `Face.getAxisInfos()` (wrapped) |
| Axis display names | ✅ `FT_Var_Axis.strid` + `FT_Get_Sfnt_Name`; ⚠️ `nameString()` is only literal for the 5 registered tags | ✅ `Face.getName(nameId, lang)` / `listNames()` |
| Axis flags (HIDDEN) | ✅ `FT_Get_Var_Axis_Flags`, `OpenType.hb_ot_var_axis_info_t.flags()` | ✅ `AxisInfo.flags` + `AxisFlags.HIDDEN` |
| Apply coordinates to **shaping** | ✅ `hb_font_set_variations` / `hb_font_set_variation` / `hb_font_set_var_coords_design` | ✅ `Font.setVariations([Variation])` (wrapped) |
| Apply coordinates to **rasterization** | ✅ `FT_Set_Var_Design_Coordinates` | ⚠️ only via the unwrapped `ft.module._FT_Set_Var_Design_Coordinates` |
| Named instance list | ✅ `num_namedstyles` + `FT_Var_Named_Style`; `OpenType.hb_ot_var_named_instance_get_design_coords` | ❌ not wrapped, not in the wasm (HB_TINY) |
| Select a named instance | ✅ `FT_Set_Named_Instance` + `hb_font_set_var_named_instance` | ⚠️ not available; equivalent coordinates can be applied via `setVariations` |
| Custom (foundry) axis tags | ✅ preserved (tag-keyed) | ✅ preserved (tag-keyed) |
| Discovery without any platform code | ✅ pure Kotlin `fvar`+`name` reader | ✅ same |

### 3.2 The four levels the question asks to separate

| | FreeType | HarfBuzz |
|---|---|---|
| **native library supports** (symbol exists) | ✅ everything, **verified in the shipped binaries**: `_FT_Get_MM_Var`, `_FT_Done_MM_Var`, `_FT_Set_Var_Design_Coordinates`, `_FT_Get_Var_Axis_Flags`, `_FT_Set_Named_Instance` (both `dyld_info -exports` on the JVM dylib and the wasm export table) | JVM ✅ everything (`hb_font_set_variations`, `hb_ot_var_*`); web ⚠️ **HB_TINY**: only `hb_font_set_variations` and `hb_ot_var_get_axis_infos` of the variation family are in the wasm |
| **the npm package / Java binding exposes** | JVM ✅ `org.lwjgl.util.freetype.FreeType.*`; web ❌ **the JS layer wraps zero variation functions** | JVM ✅ `HarfBuzz` + `OpenType` classes; web ✅ `setVariations` + `getAxisInfos` |
| **Kotlin can call it** | JVM ✅ directly; web ✅ **through the raw Emscripten module — proven by the spike (§8)** | JVM ✅ directly; web ✅ through the existing `@JsModule` externals (needs the new members declared) |
| **the project already has the seam** | ❌ `NativeFace` (`NativeFace.kt:11-24`) has no variation parameter anywhere | ❌ same |

**[F]** One correction to the settled record: `docs/plans/2026-09-17-font-bundle-findings.md`:110
states "`hb_ot_var_*` (HarfBuzz) ❌ absent from LWJGL 3.4.3 (only `hb_font_*` is
bound)". That is **not accurate**: LWJGL binds the enumeration family in a
separate class, `org.lwjgl.util.harfbuzz.OpenType` — `hb_ot_var_has_data`,
`hb_ot_var_get_axis_count`, `hb_ot_var_get_axis_infos`, `hb_ot_var_find_axis_info`,
`hb_ot_var_get_named_instance_count`,
`hb_ot_var_named_instance_get_design_coords`,
`hb_ot_var_named_instance_get_subfamily_name_id`, `hb_ot_var_normalize_coords`,
`hb_ot_var_normalize_variations`. Only the *deprecated* `hb_ot_var_get_axes` and
`hb_ot_var_find_axis` are unbound. The conclusion drawn from that line
("target-uniform discovery means reading `fvar`/`name` in `commonMain`") survives
for a different reason: a pure-Kotlin reader is still the only route that is
uniform **and** needs no seam.

## 4. Facts per layer

### 4.1 OpenType `fvar` — what a reader must produce

**[F]** (https://learn.microsoft.com/en-us/typography/opentype/spec/fvar)

- `VariationAxisRecord` = `Tag axisTag | Fixed minValue | Fixed defaultValue |
  Fixed maxValue | uint16 flags | uint16 axisNameID`; `axisSize` is 20.
- Values are **user-scale 16.16 Fixed**, not normalized: *"Axis values given in the
  variation axis record use user scale coordinates that are specific to each axis
  tag."*
- `axisNameID`: *"must be greater than 255 and less than 32768."* `name` reserves
  256–32767 for font-specific names.
- Flags: `0x0001 = HIDDEN_AXIS` ("should not be exposed directly in user
  interfaces"), `0xFFFE = Reserved`. There is no separate legacy value.
- Instances: `uint16 subfamilyNameID | uint16 flags | UserTuple | uint16
  postScriptNameID (optional)`; `instanceSize` = `axisCount * 4 + 4` (+2 with the
  optional name). *"When enumerating named instances, the default instance should
  be enumerated even if there is no corresponding instance record."*

**[F]** (https://learn.microsoft.com/en-us/typography/opentype/spec/dvaraxisreg)
Registered tags are `ital`, `opsz`, `slnt`, `wdth`, `wght`. Custom tags are
allowed and **must begin with an uppercase letter**; registered tags must not, so
the two never collide. The five are therefore a *convenience* vocabulary, never
the full domain — a reader must be tag-keyed and pass unknown tags through.

**[F]** The `avar` table (https://learn.microsoft.com/en-us/typography/opentype/spec/avar)
is optional and modifies the normalization of already-selected coordinates; it
does not change design-space values. Since both engines receive **design**
coordinates and apply `avar` themselves, a reader does not need to parse it.

### 4.2 JVM — LWJGL 3.4.3

**[F]** FreeType bindings (`org.lwjgl.util.freetype.FreeType`), all present and
resolved **non-optionally** (class init fails if the native lacks them):

```
FT_Get_MM_Var(FT_Face, PointerBuffer)
FT_Done_MM_Var(long library, FT_MM_Var)
FT_Set_Var_Design_Coordinates(FT_Face, CLongBuffer)
FT_Get_Var_Design_Coordinates(FT_Face, CLongBuffer)
FT_Set_Var_Blend_Coordinates / FT_Get_Var_Blend_Coordinates   (normalized)
FT_Set_MM_Design_Coordinates / FT_Set_MM_WeightVector         (Adobe MM only)
FT_Set_Named_Instance(FT_Face, int)
FT_Get_Default_Named_Instance(FT_Face, IntBuffer)
FT_Get_Var_Axis_Flags(FT_MM_Var, int, IntBuffer)
FT_Get_Sfnt_Name / FT_Get_Sfnt_Name_Count / FT_Get_Sfnt_LangTag
FT_IS_FIXED_WIDTH(FT_Face) / FT_HAS_MULTIPLE_MASTERS(FT_Face)
```

**[F]** Struct models: `FT_MM_Var` gives `num_axis()`, `num_designs()`,
`num_namedstyles()`, `axis(): FT_Var_Axis.Buffer`, `namedstyle():
FT_Var_Named_Style.Buffer` — **there is no `axis(int)`**, index the returned
buffer. `FT_MM_Var` does **not** implement `NativeResource`, so nothing frees it
automatically: `FT_Done_MM_Var(library, amaster)` must be called, and the
`axis`/`namedstyle` arrays must not be freed (`"Memory management of this pointer
is done internally by FreeType"`).
`FT_Var_Axis` gives `name()/nameString()/minimum()/def()/maximum()/tag()/strid()`.
`FT_Var_Named_Style` gives `coords(int): CLongBuffer`, `strid()`, `psid()`.
`FT_Face` gives `face_flags(): long`.

⚠️ **Coordinate buffers are `CLongBuffer`, not `IntBuffer`** — `FT_Fixed` is
`signed long`, i.e. 4 bytes on Windows (LLP64) and 8 on Linux/macOS (LP64).
`MemoryUtil.memAllocCLong` / `memCLongBuffer` is the portable route. (LWJGL
passes `coords.remaining()` as `num_coords`, so a buffer whose position has been
advanced by a previous call silently becomes a *reset to default* — a
footgun worth pinning with a test.)

**[F]** Constants live in `org.lwjgl.util.freetype.FreeType`:
`FT_VAR_AXIS_FLAG_HIDDEN = 1`, `FT_FACE_FLAG_VARIATION`, `FT_FACE_FLAG_MULTIPLE_MASTERS`,
`FT_Err_Ok`.

**[F]** HarfBuzz bindings: `hb_font_set_variations(long, hb_variation_t.Buffer)`
(**no explicit count** — length is the buffer's remaining), `hb_font_set_variation(long, int tag, float)`,
`hb_font_set_var_coords_design(long, FloatBuffer)`,
`hb_font_set_var_coords_normalized(long, IntBuffer)`,
`hb_font_set_var_named_instance(long, int)`, `hb_font_create_sub_font(long)`,
`hb_variation_from_string`. Enumeration lives in `org.lwjgl.util.harfbuzz.OpenType`
(see §3.2). `hb_ot_var_axis_info_t` carries
`axis_index/tag/name_id/flags/min_value/default_value/max_value` — in
**un-normalized user scale**, the same scale as FreeType.

⚠️ **[F]** `hb_ft_font_create_referenced`, `hb_ft_font_set_funcs` and
`hb_ft_font_changed` are **declared in Java but not exported by any bundled
dylib** (checked `libharfbuzz.dylib`, `-gpu`, `-raster`, `-vector`, on both
macos-arm64 3.4.3 and the cached linux 3.3.4 natives). They are
`apiGetFunctionAddressOptional`, so **calling them throws at runtime**. The
"let HarfBuzz read the FreeType face's variations" shortcut is therefore closed
on the JVM.

**[F]** Version floor: `FT_Get_MM_Var` since 2.6.1, `FT_Get_Var_Axis_Flags` since
2.8.1, `FT_Done_MM_Var` / `FT_Set_Named_Instance` / `FT_FACE_FLAG_VARIATION` since
2.9; correct fvar handling wants **2.9+**. **[M]** The shipped library is 2.14.3.

### 4.3 Web — `harfbuzzjs` 1.6.1

**[F]** The package is a class-based ESM wrapper (`dist/index.mjs`, 2322 lines).
Relevant members, all present in the installed 1.6.1 (`dist/index.d.mts:348-352`,
`:1188-1219`, `:1458-1467`):

- `Face.getAxisInfos(): Record<string, AxisInfo>` where
  `AxisInfo = { axisIndex, tag, nameId, flags, min, default, max }`, values in
  **"un-normalized, user scales"**.
- `Face.getName(nameId, language): string` and `Face.listNames(): NameEntry[]`.
- `Face.referenceTable(table): Uint8Array | undefined` — ⚠️ returns a **live
  `HEAPU8.subarray`**, not a copy.
- `Font.setVariations(variations: Variation[]): void` — *"overrides all existing
  variations set on the font. Axes not included in `variations` will be
  effectively set to their default values."*
- `Variation(tag, value)` with `Variation.fromString("wght=500")`.
- `Font.subFont()` (deep-copies the parent's coordinates).

**[F]** The implementation is thin and readable: `getAxisInfos` calls
`exports.hb_ot_var_get_axis_infos` into a **`stackAlloc(2048)` = 64-axis buffer**
(`index.mjs:262-283`), so a font with more than 64 axes silently truncates;
`setVariations` writes an `hb_variation_t` per entry and calls
`hb_font_set_variations` (`index.mjs:1173-1181`).

**[F]** The wasm is an **`HB_TINY`** build. Parsing its export table (152 entries)
gives exactly `hb_font_set_variations`, `hb_ot_var_get_axis_infos`,
`hb_variation_from_string`, `hb_variation_to_string` from the variation family.
Absent from the binary: `hb_font_set_var_coords_design/normalized`,
`hb_font_set_var_named_instance`, `hb_ot_var_find_axis`,
`hb_ot_var_get_axis_count`, `hb_ot_var_get_named_instance_count`,
`hb_face_get_table_tags`. This is a **build** limitation, not a wrapper gap.

**[F]** There is **no escape hatch**: `Module`/`exports` are module-private
closure state (`index.mjs:3-26`), instances expose only `ptr`, and the package
`exports` map publishes only `"."` and `"./dist/*.wasm"`, so
`harfbuzzjs/dist/harfbuzz.js` is not importable by specifier. Nothing is lost:
the two entry points above are the whole built variation surface.

**[F]** `docs/plans/2026-09-16-r6-text-touchpoint.md`:206-211 already records
"HarfBuzz carries variations on the font, not on the face" and names web
selection as `Font.setVariations`; both hold.

### 4.4 Web — `@zkl2333/freetype-wasm` 2.14.3

**[F]** The convenience layer wraps **no** variation function: `dist/index.d.ts`
declares only `FT` constants (no MM/VAR constants), `initFreeType`, `FreeType`
(`version/newFace/destroy/module/offsets/library`) and `Face`
(`ptr/info/setPixelSize/setCharSize/charIndex/selectCharmap/loadGlyph/kerning/destroy`).
A `grep -i 'var|axis|MM_|Named|fvar|coordinate'` over `index.d.ts` and
`index.mjs` returns nothing.

**[F]** The raw layer is a **documented, first-class API**: `README.md`:73-82
says *"**Raw** — `ft.module` is the full Emscripten module
(`ccall`/`cwrap`/`getValue`/`setValue`/`HEAPU8`/`_malloc`/`_free`/`addFunction`).
Combined with `ft.offsets` (wasm32 struct field offsets) this reaches **any**
FreeType function the convenience layer does not wrap"*, and `index.mjs`:87-89
carries the same statement in code comments. `Face.module` is the same object.

**[F]** `dist/freetype.mjs` assigns **203** symbols onto the module under their
public names, e.g. `_FT_Get_MM_Var=Module["_FT_Get_MM_Var"]=wasmExports["dc"]`.
The variation family is there in full: `_FT_Get_MM_Var`, `_FT_Done_MM_Var`,
`_FT_Set_Var_Design_Coordinates`, `_FT_Get_Var_Design_Coordinates`,
`_FT_Set_Var_Blend_Coordinates`, `_FT_Set_Named_Instance`,
`_FT_Get_Var_Axis_Flags`, `_FT_Get_Default_Named_Instance`,
`_FT_Get_Sfnt_Name`, `_FT_Get_Sfnt_Name_Count`, `_FT_Get_Multi_Master`.
There is no `Module.wasmExports` (only `instance.exports`, assigned per symbol);
the public-name map above is the authoritative index.

⚠️ **[F]** `dist/offsets.mjs` / `struct-offsets.json` ship **9 structs**
(`FT_FaceRec`, `FT_GlyphSlotRec`, `FT_Glyph_Metrics`, `FT_Bitmap`,
`FT_Size_Metrics`, `FT_SizeRec`, `FT_Vector`, `FT_CharMapRec`, `pointerBytes`)
and **omit `FT_MM_Var` / `FT_Var_Axis` / `FT_Var_Named_Style`**. Reading the
descriptor means hand-writing the wasm32 layout:

```
FT_MM_Var  { u32 num_axis@0; u32 num_designs@4; u32 num_namedstyles@8;
             ptr axis@12; ptr namedstyle@16 }
FT_Var_Axis{ ptr name@0; i32 minimum@4; i32 def@8; i32 maximum@12;
             u32 tag@16; u32 strid@20 }   // stride 24, values 16.16 Fixed
```

**[M]** That layout is verified against the shipped build: axis tags, min/def/max
and the design-coordinate round-trip are all self-consistent.
`FT_FaceRec.face_flags` **is** in the shipped offsets (offset 8), so
`FT_HAS_MULTIPLE_MASTERS` can be read without new constants.

⚠️ **[F]** `Module.HEAP8/16/32/U8/U16/U32/F32/F64` are reassigned whenever the
heap grows (`updateMemoryViews()`), so a heap view must be re-read from the
module on each use, never cached across a call that can allocate.

### 4.5 Family, subfamily and monospaced metadata

**[F]** `name` table IDs: 1 family, 2 subfamily, 16 typographic family (falling
back to 1), 17 typographic subfamily (falling back to 2). The normative
monospace flag is `post.isFixedPitch`; `OS/2.panose[3]` (`bProportion`) is only a
heuristic (https://learn.microsoft.com/en-us/typography/opentype/spec/name ,
`.../post`, `.../os2`).

Per target:

| Route | JVM | Web |
|---|---|---|
| Pure Kotlin reader over the load-time `ByteArray` | ✅ | ✅ |
| FreeType | ✅ `FT_Get_Sfnt_Name*`, `FT_IS_FIXED_WIDTH` | ⚠️ via `_FT_Get_Sfnt_Name` + `face_flags` offset |
| HarfBuzz | ✅ `hb_face_reference_table` is bound (`HarfBuzz.java`), plus `blob_get_data` | ✅ `Face.referenceTable("name")` |
| harfbuzzjs name helpers | — | ✅ `Face.getName` / `listNames` |

**[J]** This is uniformly obtainable, and `Font.load(bytes: ByteArray)`
(`Font.kt:93-97`) already holds the whole payload before it is copied into
engine memory, so a `commonMain` reader needs **no seam widening at all**.

### 4.6 Kotlin interop

**[F]** `@file:JsModule` is the mechanism on both web targets, and it is already
in production here (`HarfBuzzWebExternals.kt:1`, `FreeType.kt:1`), including the
recorded constraint that such a file may declare only `external` members
(chunk `34`). New members (`Face.getAxisInfos`, `Font.setVariations`,
`Variation`, `Face.getName`) are a pure externals widening — no dependency
change, no webpack change.

**[F]** Kotlin/Wasm interop allows only
`Int/Float/Double→Number`, `Long/ULong→BigInt`, `String`, `Boolean`,
`JsAny`/subtypes, `JsReference` and function types; "Other types — Not
supported" (`https://kotlinlang.org/docs/wasm-js-interop.html`). Typed arrays go
through `org.khronos.webgl` / `kotlinx-browser` adapters, which this module
already uses (`Uint8Array`, `DataView`, `toInt32Array`-style helpers).

⚠️ **[F]** **No kotlinlang.org page documents calling Emscripten-built functions
or `ccall`/`cwrap`/`HEAP*` from Kotlin/WasmJS.** The spike proved the pattern
works (§8), but it remains an undocumented one: it carries no compatibility
promise from the Kotlin side, which is why §10 makes a Kotlin upgrade a reason to
re-run the spike.

## 5. Shaping × rasterization consistency

**[F]** The two engines hold coordinates independently. HarfBuzz stores
`font->coords` (normalized 2.14) and `font->design_coords` on the `hb_font_t`;
FreeType stores the variation state on the `FT_Face`. Neither reads the other
unless the application wires them (`hb_ft_font_changed()` for FT→hb,
`hb_ft_hb_font_changed()` for hb→FT, automatic since HarfBuzz 11.0.0 — both
documented at https://harfbuzz.github.io/harfbuzz-hb-font.html and in
`src/hb-ft.cc` / `src/hb-ft.h`).

**[F]** In this project both directions are closed:

- The JVM face builds the HarfBuzz font with `hb_font_create(face)`
  (`NativeFaceJvm.kt:162`), i.e. **pure hb-ot font funcs**, not `hb_ft_*`; and the
  `hb_ft_*` entry points are not even exported by the shipped natives (§4.2).
- The web face uses `HarfBuzzFont(face)` on the `HB_TINY` build, which has no
  `hb_ft_*` at all.

**Therefore the engine must apply one coordinate set to both engines.** The
scenarios:

| Scenario | Observable consequence |
|---|---|
| HarfBuzz gets coordinates, FreeType does not | Advances/offsets are for the requested instance; the rasterized outlines are the default instance. Text is laid out for one weight and drawn at another — the glyph boxes no longer match the advances. |
| FreeType gets coordinates, HarfBuzz does not | Two `Font` objects of the same face would render two different rasters with identical layout — silently wrong, and undetectable from a single layer's test. |
| `opsz` applied automatically by one layer | **Cannot happen**: **[F]** neither FreeType nor HarfBuzz writes `opsz` on its own. FreeType's only `opsz` occurrence is axis naming (`ttgxvar.c:3136`, `FT_Var_Axis.name = "OpticalSize"`); HarfBuzz only *reads* `font->ptem` as a query fallback in `hb_style_get_value` (`src/hb-style.cc`). An `opsz` divergence can therefore only come from the engine applying it to one side. |
| Two configurations of the same face at once | Must be two independent native states: two `hb_font_t` **and** two `FT_Face`. HarfBuzz already supports this (`hb_font_create` per config, or `hb_font_create_sub_font`, which deep-copies `num_coords`/`coords`/`design_coords`); FreeType supports it by calling `FT_New_Memory_Face` again on the same retained buffer, or by a second wrapper `newFace`. |

**[J]** The variation state therefore belongs neither to the family nor to the
face, but to the **resolved, configured font instance** — the object that owns
today's per-size atlas map (`Font.kt:20-21`). Putting it on the face would make
the face single-configuration.

## 6. Lifecycle, thread-safety and cache risks

- **[F] `FT_MM_Var` ownership.** `FT_Get_MM_Var` allocates; `FT_Done_MM_Var(library, amaster)`
  frees. The `axis`/`namedstyle` arrays belong to it. The JVM library handle is
  already a file-private `by lazy` (`NativeFaceJvm.kt:192-199`); on web `ft.library`
  is public. A leak here is invisible to the engine's leak detector: it is not a
  `KGEResource`.
- **[F] Web memory duplication.** `harfbuzzjs`'s `Blob(data)` copies the payload
  into wasm memory, and `freeTypeModule().newFace(data)` copies it again
  (`index.mjs` `newFace`: `_malloc` + `HEAPU8.set`). Each configured instance
  therefore costs roughly **2× the font size** in wasm heap; `Face.destroy()`
  frees only the FreeType copy. Sharing one payload across N configurations
  (`hb_font_create_sub_font`, and one FreeType face per config) is possible but
  is not what the current seam does today — `Font.load` builds a complete new
  face per call.
- **[F] Axis names need the `name` table, not `FT_Var_Axis.name`.** FreeType
  documents `name` as *"Not always meaningful for TrueType GX or OpenType Font
  Variations"*; the source assigns a literal only for `wght`/`wdth`/`opsz`/`slnt`/
  `ital`, and points unknown tags at an undocumented buffer. Resolve `strid`
  against `name` (or read `name` in Kotlin).
- **[M] The `ftSizePx` cache is safe across a coordinate change.** Measured on
  JVM/FreeType 2.14.3: with the pixel size set once, rendering `A` at 16 px in the
  sequence `wght` 900 → 400 → 900 → 100 → 400 (no `FT_Set_Pixel_Sizes` in
  between except where noted) returned `16978`, `9983`, `16978`, `3354`, `9983` —
  each matching the coordinate in force. `NativeFace.rasterize` therefore does
  **not** need variation-aware invalidation; the existing `ftSizePx` guard stays
  correct. Whether this is contract or implementation detail is unverified (§10).
- **[F] Atlas keys.** `Font.atlasesBySizePx` / `gpuAtlasesBySizePx` are keyed by
  `sizePx` only (`Font.kt:20-21`, `:51-55`). Correct as long as one `Font` is one
  configuration; wrong the moment a single object serves two coordinate sets.
- **[F] Coordinates are design-space on both sides**, only the encoding differs:
  FreeType `FT_Fixed` 16.16 (`value * 65536`), HarfBuzz `hb_variation_t.value` a
  plain `float`, and `harfbuzzjs`'s `Variation.value` likewise. **[F]**
  `hb_font_set_variations` *"overrides all existing variations… Axes not included
  will be effectively set to their default values"*, so a partial application can
  silently reset an axis — pass the complete set.
- **[F] Threading.** Browsers are single-threaded; on the JVM, neither an
  `FT_Face` nor an `hb_font_t` is documented as thread-safe for concurrent
  mutation, and the existing model is one face per `Font` behind the caller's own
  discipline. Nothing in this research changes that.
- **[F] `referenceTable` is a live view** into wasm memory and is invalidated by
  heap growth; copy before storing.

## 7. Alternatives compared

### 7.1 Where the variation state lives

| Option | Verdict |
|---|---|
| On the family | ✗ A family holds several files/faces; it has no single coordinate set. |
| On the face | ✗ Makes the face single-configuration; two simultaneous configurations become impossible without cloning. |
| On the resolved font instance (today's `Font`/`KGETextFont`) | ✓ Matches the engine's ownership of the atlas, keeps `sizePx`-keyed atlases valid, and gives one coordinate set to push into both engines. |
| On a native clone per configuration (`hb_font_create_sub_font` + per-config `FT_Face`) | ✓ As an **implementation** of the option above — cheaper on web, since the face/blob can be shared. Not required for correctness. |

### 7.2 Who enumerates the axes

| Option | Platform-uniform | Cost |
|---|---|---|
| Pure Kotlin `fvar` + `name` reader in `commonMain` | ✅ | A new parser (~200 lines) and its tests; already the recorded direction (`docs/plans/2026-09-16-r6-text-touchpoint.md`:201-205). Works for third-party fonts, covers named instances, and needs **no seam**. |
| Per-backend native enumeration (`FT_Get_MM_Var` / `getAxisInfos`) | ❌ two implementations, different name resolution | Avoids the parser; but the web one is **not wrapped**, so it drags the escape hatch into discovery too — strictly more risk for the same result. |
| HarfBuzz `referenceTable("fvar")` + Kotlin parse | ✅ | One seam function (JVM `hb_face_reference_table` is bound; web `Face.referenceTable` is wrapped), reuses one parser. Viable, but the load-time `ByteArray` already exists, so it buys nothing. |

### 7.3 The minimum common interface

Evaluated against the stated constraints (no parameter without observable
effect, no property without a consumer, no FreeType/HarfBuzz types in the core,
axes meaningless for the bitmap family, custom tags preserved, no provisional API,
minimum public surface, ≤2 KDoc lines).

**A. Generic axes by tag + typed conveniences.** A tag-keyed axis descriptor
(`tag: String`, `min/default/max: Float`, `name: String?`, `hidden: Boolean`) plus
typed conveniences (`wght`, `wdth`, `opsz`, `slnt`, `ital`) as lookups over that
map. Preserves custom tags by construction; the conveniences are thin and each
has an observable effect. Cost: a small public value type and a lookup API, and
the conveniences must be justified by a named consumer (principle 7).

**B. Typed value classes only (`KGEFontWeight`, `KGEFontWidth`).** Smallest
surface. ✗ **Refuted by the data**: 5 registered tags exist and the bundled
files carry 2 axes each, but a third-party font may carry any foundry tag —
`wght`+`wdth` covers the shipped fonts and nothing else. A typed-only API would
have to grow a new type per axis it learns about, and would silently drop custom
axes.

**C. Defer everything; use the files' default instances.** ✗ **Not viable as a
permanent answer, but viable as the next round.** The shipped fonts default to
`wght = 400` (`docs/plans/2026-09-17-font-bundle-findings.md`:103-104), roman and
italic are separate files, and neither the roman nor an italic axis selection is
needed to load them. It is the correct *interim* state (§9) — it just does not
answer the question, and the model should not be built so that adopting A later
means breaking it.

**[J] A is the only shape that satisfies the constraints**, and only if the
conveniences are added against a consumer. **But A is a *selection* API; it does
not need a *discovery* API in the core** — discovery can stay inside the TTF
module (pure Kotlin), and only the coordinate set crosses into the seam.

### 7.4 The web rasterization path

| Option | Verdict |
|---|---|
| Call the unwrapped exports through `ft.module` (the package's documented raw layer) | ✓ **Chosen and proven** by the spike. No fork, no new dependency, ships the exact capability. |
| Write a small JS/TS wrapper that re-exports the six calls | ✗ Imports the same unwrapped module from Kotlin **plus** an extra artifact to build and ship; the Kotlin externals are no harder than the JS ones. |
| Fork or rebuild `@zkl2333/freetype-wasm` | ✗ Unjustified: the capability is already in the shipped wasm. |
| Replace the dependency (`fontkit`, `opentype.js`) | ✗ These are outline/variation engines, not rasterizers; adopting one replaces the settled HarfBuzz+FreeType stack and loses the byte-identical cross-platform rasters the module pins. Recorded as available (fontkit 2.0.4 has `variationAxes`/`namedVariations`/`getVariation` with a browser bundle; opentype.js 2.0.0 has `Font.variation`), not recommended. |
| Browser `FontFace` / `font-variation-settings` | ✗ Declarative inputs to the UA's own text pipeline; they expose no per-instance outlines and cannot feed an application-side atlas. |

## 8. Conclusion

**`SUPPORT NOW`.**

Everything is proven, including the last link:

- **[M]** JVM: enumerate, apply, read back, reset, free — exercised end to end on
  FreeType 2.14.3 and HarfBuzz 14.3.0.
- **[M]** Web, from Kotlin: the same, on `js` and `wasmJs` (see the spike result
  below).
- **[F]** The Kotlin→`harfbuzzjs` link already existed in production for other
  members, so `setVariations`/`getAxisInfos` are a mechanical externals widening.

### Spike result (2026-10-04)

A throwaway spike ran on `js` and `wasmJs` (ChromeHeadless) over the shipped
`Roboto[wdth,wght].ttf`, declaring the raw FreeType module and the `harfbuzzjs`
variation API as test-source-set externals — no production file was touched. Both
targets returned, verbatim:

```
raw module members callable  _FT_Get_MM_Var, _FT_Set_Var_Design_Coordinates,
                             _FT_Done_MM_Var, _malloc, _free  (typeof function)
enumerate   rc=0  num_axis=2  num_namedstyles=18
            wght 100/400/900   wdth 75/100/100
rasterize   A@16  wght=400 -> sum 9983   box 11x12 bearing (0,-12)
            A@16  wght=900 -> sum 16978  box 12x12 bearing (-1,-12)
            A@16  back to 400 -> sum 9983
ownership   _FT_Done_MM_Var=0;  num_coords=0 -> 0, sum restored to 9983
shaping     getAxisInfos agrees; setVariations(wght=900)
            moves A xAdvance 668 -> 698 (26.6)
```

**[M] Every number matches the JVM measurement**, including the shaped advance,
so the parity is measured rather than assumed. Reading the descriptor through
the hand-written wasm32 layout works, and so does `_malloc` +
`HEAP32` + `_free` for the 16.16 coordinate array. `numDesigns = -1` is the raw
`0xFFFFFFFF` FreeType writes for a non-discrete design space; it was printed, not
asserted.

The three spike files were deleted afterwards, the post-delete suites are green
(109 tests per target, 0 failures), and `git status` shows only this document.

Two residuals survive and are carried in §10: the `_FT_*` member names are
minified-build internals with no published stability promise, and the
`FT_MM_Var`/`FT_Var_Axis` offsets are hand-written and verified for 2.14.3 only.
Neither is a blocker, but both mean a dependency bump must **re-run this spike**
rather than trust it.

### The spike spec it was run against

- **Platforms covered:** `wasmJs` (ChromeHeadless, the strict case) and `js`;
  JVM only if a same-host parity number is wanted (the numbers below are already
  measured).
- **Scratch (all deleted at the end):** one test file under
  `kge-text-ttf/src/webTest/…/VarAxisSpikeTest.kt` and one temporary externals
  file declaring the module object; nothing under `src/commonMain`, no build
  change, no new dependency. Note the shared `kge-text-ttf/karma.config.d/await-suites.js`
  shim (chunk `35`) and the module's existing `commonTest` fixture (`kge-font-roboto`).
- **Assertions:**
  1. `ft.module` exposes `_FT_Get_MM_Var`, `_FT_Set_Var_Design_Coordinates`,
     `_FT_Done_MM_Var` as callable members on **wasmJs** (the binding compiles and
     links).
  2. On `Roboto[wdth,wght].ttf`: `FT_Get_MM_Var` reports 2 axes with tags
     `wght`/`wdth` and min/def/max `100/400/900` and `75/100/100` — the same
     values the JVM probe returned and the same ones `Face.getAxisInfos()` reports.
  3. `FT_Set_Var_Design_Coordinates(wght=900)` then rasterize `A` at 16 px:
     the coverage sum is **16978**; back at `wght=400`, **9983**. The `400` sum
     and the `11x12 (0,-12)` box are the round D hand-derived table (chunk `36`);
     the `900` sum is this document's JVM measurement, so assertion (3) is a
     **cross-platform parity** check against a number the same run also produces
     on the JVM — not a snapshot of the web's own output.
  4. `FT_Done_MM_Var` returns 0 and the reset (`num_coords = 0`) restores 9983.
  5. `face.getAxisInfos()` agrees with (2) (same tags and the same
     `100/400/900`, `75/100/100`), and
     `font.setVariations([Variation("wght", 900)])` changes the shaped advance of
     a fixed string — compared against the JVM's shaped advance for the same
     string in the same run, since the two platforms' HarfBuzz builds differ
     (14.3.0 vs 14.4.0).
- **Objective success criteria:** the spike test compiles and passes on `wasmJs`
  and `js`; assertion (3) matches the JVM numbers exactly; no production file
  changed.
- **Deleted at the end:** both spike files, the generated test bundles and any
  `.tmp/` scratch; `git status` must be clean apart from this document.

**Result: it did not fail**, so the fallback (a small hand-written JavaScript
module in `webMain`) was not needed.

## 9. Recommendation to the touch-point

1. **Ship default-instance loading first, and shape the model so axes are
   additive.** Loading Roboto roman and italic as distinct faces needs no axis
   support at all: the shipped module already treats each family as one file
   (`kge-font-roboto/fonts/roboto/Roboto[wdth,wght].ttf`), and an italic release is
   a separate file with its own axes, so a family can adopt both atomically with
   the existing all-or-nothing discipline and `CompositeResource`. (The italic
   file itself is not in the repository today — see §10.)
2. **The spike is done and green (§8)** — no axis API is blocked on further
   proof. What the round now owes is the production seam: the raw FreeType
   variation calls behind `NativeFace`, plus the `getAxisInfos`/`setVariations`
   externals, each pinned by a test that asserts raster **and** shaping.
3. **Put discovery in the module, in `commonMain`, over the load-time
   `ByteArray`.** `Font.load` (`Font.kt:93-97`) already has the bytes. This keeps
   the seam at zero new names, honours scope discipline, and produces the same
   answer on all three targets. FreeType's `FT_Get_MM_Var` stays available as a
   cross-check, not as the source.
4. **Put the variation state on the configured instance**, never on the family or
   the face (§7.1), and keep the per-size atlas keyed as it is.
5. **Push one coordinate set into both engines explicitly** and pin it with a test
   that asserts raster *and* shaping at a non-default instance — a single-layer
   test cannot catch the divergence in §5.
6. **If an axis API is published, publish option A** (§7.3) and only against a
   named consumer. Until then, exposing the *coordinate set* on the instance is
   the whole feature, and the enumeration stays internal to the module.

### On the model proposed for this discussion

- **`KGEFontFamily` / `KGEFontFace` / `KGETextFont` / `KGEFontSize`
  (`Int` value class with `Int.fontPx`).** **[J]** Consistent with the engine's
  ownership model, and `KGETextFont` is exactly the "configured instance" §5
  calls for — provided the *axis coordinates* live on `KGETextFont` and not on
  `KGEFontFace`. The core/bitmap side is unaffected as long as nothing on the
  shared supertype mentions axes.
- **Family and `KGETextFont` as resources.** **[J]** Consistent with
  `KGEResource`/`ResourceScope` and with `Font : KGEResource` today
  (`Font.kt:17-19`). A family owning several faces is a composite; note the web
  memory duplication in §6 when sizing that.
- **A family from several files; Roboto and Roboto Mono distinct; roman + italic
  loaded atomically.** **[J]** All three are ordinary consequences of the model
  and are not affected by the axis question. No `faceIndex` is needed — the
  indexed-face parameter was removed for a reason (chunk `34`) and a family of
  separate files sidesteps it.
- **A TTF specialization of `KGEFontFace` exposing axes.** **[J]** Feasible
  (§3.1) but **not yet justified**: `KGEFontFace`'s consumer is the draw path,
  which needs coordinates, not the axis list. Deferring costs nothing because
  discovery is additive — a Kotlin reader can be added later without breaking a
  coordinate-only API. Publishing axes now would also risk the "no consumer"
  finding the unification analysis already recorded
  (`docs/plans/2026-10-01-text-api-unification-findings.md`:§6).
- **The common interface must not force the bitmap font to fake weight/width/
  axes.** **[J]** Satisfied automatically if the common type carries only what
  both can honour and the axis vocabulary lives in the TTF module. Any axis
  member on the common type is a defect by construction.
- **Shaping and rasterization must use consistent coordinates.** **[J]** Agreed,
  and it is not automatic in this stack (§5) — it is an explicit engine
  responsibility and belongs in the round's test contract.

## 10. Uncertainties and unverified facts

- **[M] The JVM measurements are from one host.** `macos-arm64` natives, LWJGL
  3.4.3, FreeType 2.14.3, JDK 21, Roboto 3.015. The symbol inventory for
  `linux-x64` was confirmed only on the cached **3.3.4** natives (`nm -D`); the
  3.4.3 Linux natives were not cached and were not downloaded. Windows was not
  inspected at all, and it is the platform where `FT_Fixed`'s C `long` is 4 bytes
  — the `CLongBuffer` note in §4.2 is a reasoned risk, not a measurement.
- **The "FreeType re-derives the size after a coordinate change" result (§6) rests
  on one probe and one host.** FreeType does not document this as a contract; a
  version bump on either side should re-measure it, exactly as chunk `36` says of
  the raster table.
- **The `_FT_*` module member names are minified-build internals.** They are the
  package's documented API (README §API "Raw"), but a future patch of the same
  npm version could rename the internals while keeping the convenience layer; the
  package publishes no stability promise for the raw layer. The spike should fail
  loudly if a member is missing rather than degrade silently.
- **`FT_MM_Var` / `FT_Var_Axis` offsets are hand-written** because the package
  does not ship them (§4.4). They are verified for 2.14.3 only.
- **Kotlin/WasmJS + Emscripten interop is undocumented** (§4.6). It works for
  this Kotlin 2.4.10 + these packages (§8), but nothing guarantees it across a
  Kotlin or npm bump; a bump must **re-run the spike**, not trust this document.
  The same applies to the minified `_FT_*` member names and the hand-written
  struct offsets.
- **Not verified:** whether `harfbuzzjs`'s 64-axis `stackAlloc` cap can be hit by
  any real font; whether `getAxisInfos()`'s `Record<tag, AxisInfo>` silently
  drops a font with duplicate tags (not permitted by `fvar`, so untested);
  whether a JVM `hb_ot_var_axis_info_t` read agrees numerically with
  `FT_Get_MM_Var` on the shipped fonts (only the FreeType side was measured);
  nothing about italic files was measured: `kge-font-roboto/fonts/` ships only
  `roboto/Roboto[wdth,wght].ttf` and `roboto-mono/RobotoMono[wght].ttf`, and the
  upstream italic release's exact file name and axis set were not fetched.
- **No part of this research modified any source, test, build, plan, decision or
  roadmap file.** The throwaway probes ran in `/tmp` and were removed.
