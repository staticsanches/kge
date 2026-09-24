# `R6` round D — FreeType rasterization + glyph cache/atlas: touch-point

**Status: decided with the owner, 2026-09-22.** Confirmed at this touch-point:
(1) `Font.load` becomes `suspend`; (2) the FreeType library is process-scoped and
the per-font face is the resource; (3) round D ships the full atlas; (4) round D
registers its resources in a `ResourceScope` through a C7-style capability
service, which adopts an already-loaded `Font` while the `Font` keeps ownership
of its atlases (decision 6).

**Date:** 2026-09-22. Touch-point for round **D** of the text work
(`kge-text-ttf`), the module's fourth round. Rounds: A bitmap (core) → B
scaffold → C face + shaping + layout → **D FreeType rasterization + glyph
cache/atlas** → E CPU blit + addons + decal. Material:
`docs/plans/2026-09-16-r6-text-touchpoint.md` (stack decision and the raster
parity spike — do not re-research), the round C micro-plan
(`docs/plans/2026-09-17-kge-text-ttf-round-c-microplan.md`), decisions chunks
`31`, `34` and `35`. The micro-plan is written just-in-time on top of this; it
is the test contract.

## Scope

Ships: FreeType in production on both targets, the seam's rasterization
operation and its coverage carrier, the per-size glyph atlas/cache owned by
`Font`, and the module's capability service with its scope registration. Does
**not** ship: any blit (CPU or decal), addons, `drawString*`/`getTextSize*` —
all round E. Public surface added by D: `Font.load` becomes `suspend`, and the
capability service is introduced (decisions 2 and 6).

## Established (not re-decided here)

- Stack HarfBuzz (shaping) + FreeType (rasterization), Latin/LTR, the opt-in
  `kge-text-ttf` module (touch-point 2026-09-16).
- **Raster parity is already measured:** JVM (`lwjgl-freetype`, FreeType 2.14.3)
  × web (`@zkl2333/freetype-wasm` 2.14.3), `FT_LOAD_DEFAULT` +
  `FT_RENDER_MODE_NORMAL` (hinting on), produced **identical** coverage bitmaps
  (touch-point spike table). Both sides are the same FreeType version, so the
  "AA differences tolerated" allowance is moot for D: it pins byte-level raster
  values instead of tolerances.
- Round C built the seam as three names (`NativeFace`, `createNativeFace`,
  `closeNativeFace`) and the public `Font`/`ShapedRun`/`TextMetrics`; shaping
  advances/offsets come from HarfBuzz at 26.6 scale.

## Reference evidence

**`main` has no font-face code** — verified: no FreeType, HarfBuzz or glyph
atlas source (`git grep` over `main`); its only font code is the olc bitmap
text, which `C7` already brought into `kge-core` in round A. There is no
Kotlin-level baseline to regress and no olc parity to keep: olc v2.30 has no TTF
API.

The only Kotlin precedent is the `fontDevelopment` branch (`a867d1d`,
`kge-core/.../font/`: `FreeTypeFace`, `GlyphAtlas`, `TextShaper`). Evidence, not
a mandate — taken and rejected item by item:

| `fontDevelopment` | D |
|---|---|
| `FT_Load_Glyph(FT_LOAD_RENDER)`, bitmap width/rows/pitch/`num_grays`, `bitmap_left`/`bitmap_top` | taken (normalized at the seam, decision 1) |
| glyph advance read from the FT slot | **rejected** — advances come from HarfBuzz only (decision 1) |
| 512² chart textures, shelf rows, a new chart when no row fits | taken as the atlas shape (decision 4) |
| global mutable `Configuration.glyphChartWidthHeight` / `RowHeightThreshold` / `RowHeightIncrease` (+ system properties) | **rejected** — global mutable defaults are banned by the facade contract, and the two heuristics are packing-density tuning with no consumer |
| each glyph is a GPU `PartialDecal` over a chart texture | the white-RGB + alpha-coverage encoding is taken; the GPU carrier is E's |
| refcounted global `FTLibrary` behind a `WeakReference` | **rejected** for process-scoped library + per-font face (decision 3) |
| `withSize` re-sets the FT pixel size and locks | size-change caching taken, lock dropped (engine-confined, single-threaded) |
| retained `hb_buffer_t` (reset per shape), an `IntBuffer` code-point feed (`hb_buffer_add_codepoints`), `hb_ft_font_create` and script/language caches | **rejected for D** — the raster path is fed glyph ids, not code points, so there is no feed to pool, and the charmap walk (`FT_Get_First_Char`/`Next_Char`) has no consumer because HarfBuzz already supplies the ids. Round C already replaced the shaping-side pooling with a per-call buffer. If per-frame shaping cost matters it is an **E** question — cached shaped runs before buffer pooling — to be measured there, not a raster one. |

## Decision 1 — rasterization joins the seam

```kotlin
internal expect class NativeFace {
    fun shape(codePoints: IntArray, sizePx: Int): List<ShapedGlyph>
    fun metrics(sizePx: Int): TextMetrics
    fun rasterize(glyphId: Int, sizePx: Int): GlyphCoverage
}

/** Coverage of one rendered glyph: row-major, top-down, one byte per pixel. */
internal class GlyphCoverage(
    val width: Int,
    val height: Int,
    /** Pen to bitmap top-left, in whole pixels, y down. */
    val bearing: Int2D,
    /** `width * height` bytes; empty when the glyph has no ink. */
    val coverage: ByteArray,
)
```

- **Coverage is normalized at the seam.** `FT_Bitmap` layout, `pitch` (and its
  sign), `num_grays` and the pixel mode stay inside the platform actual; common
  code never sees a platform struct (roadmap corollary: dependency pressure
  stays at the seam). A non-gray bitmap (MONO, BGRA color strikes) fails fast —
  the outline path with `FT_RENDER_MODE_NORMAL` is the only supported result.
- **The seam carries no advance.** Advances and offsets come exclusively from
  the shaped run; mixing FreeType's hinted slot advance into layout would make
  the pen disagree with HarfBuzz's kerning/ligatures.
- **Integer pixels only.** `bearing` is whole pixels so a blitter adds it to a
  pen; subpixel positioning is out of scope.
- Rasterization is a single-glyph call; the atlas (decision 4) is what makes it
  once per (size, glyph).

## Decision 2 — `Font.load` becomes suspend

`@zkl2333/freetype-wasm` exposes its library only through
`initFreeType(): Promise<…>` — the Emscripten module instantiates
asynchronously. `harfbuzzjs` is importable synchronously only because its own
top-level await resolves at module load. The FreeType library therefore cannot
be in hand before the first load without blocking (impossible on web) or a JS
shim.

**Chosen:** `suspend fun load(bytes)` / `suspend fun load(base64)` and
`internal suspend expect fun createNativeFace(bytes)`. `shape`/`metrics` stay
synchronous.

- Principle 5: the common contract takes the full form on both targets; the JVM
  actual simply never suspends.
- Precedent: `ImageService.load`/`save` are suspend (`S6`), and every kotest
  test lambda is already suspend, so the module's tests need no new
  infrastructure.
- **Rejected:** a web JS shim with a top-level await that re-exports the
  initialized module (keeps `load` synchronous). No consumer needs synchronous
  load — engine setup and addon wiring already run in suspend context — and it
  buys another hidden async module entry of exactly the family behind the
  phantom-green incident (`#35`), plus a relative-import arrangement that
  differs between js and wasmJs.

## Decision 3 — the library is process infrastructure; the face is the resource

- One file-private, **single-flight**, lazily initialized FreeType library per
  process: the JVM `FT_Library` and the web module. The **per-font** `FT_Face`
  is created at load and released inside `closeNativeFace`, ordered with the
  HarfBuzz handles and before the payload.
- Rationale: on web the module is a single ~830 KB wasm instance with an async,
  process-wide init — a per-font library is impossible there, and principle 5
  makes the common design uniform, so JVM's library is process-scoped too. This
  mirrors round C's recorded web reality for HarfBuzz (module left to the
  platform, no deterministic module teardown) while every per-font native
  handle stays inside the face's resource.
- Leak detection is unchanged: the face wrapper already reports "font face"; the
  FT face is released by the same clean action.
- **Rejected:** `main`'s refcounted `FTLibrary` (`WeakReference`, destroy on the
  last face) — extra lifecycle state, and on web the last-face path would tear
  down and re-instantiate the wasm module.

**Verified in the wrapper source** (`@zkl2333/freetype-wasm` `src/index.mjs`,
2026-09-22) — the "one module" rule is ours to enforce, not the package's:

- **`initFreeType()` is not idempotent.** It caches only the dynamic import of
  `freetype.mjs`; every call constructs a **new** Emscripten module — new wasm
  instance, new `FT_Library`, new heap. A naive per-font init would pay ~830 KB
  of wasm and a whole heap per font, and closed fonts would leave their modules
  behind.
- **The shared module must be single-flight, not a nullable variable.**
  `createNativeFace` is suspend and the init awaits, so two concurrent
  `Font.load`s would both observe "not initialized" and instantiate twice; a
  `Deferred` (or a mutex-guarded lazy) is what makes it once.
- **N fonts = N `Face`s in the one module.** FreeType does not copy the font
  data, so the wrapper `malloc`s a per-face copy into the heap and frees it in
  `Face.destroy()`. That is per-font heap, not per-font instance — and it is why
  the web staging buffer is released as soon as both faces exist. Consequence:
  on web a loaded font holds two wasm copies of its payload (HarfBuzz's blob and
  FreeType's face buffer).
- **`FreeType.destroy()` destroys every face the instance opened.** We call
  `Face.destroy()` per font and never the module's `destroy()`.
- **The web bitmap comes back copied but not row-normalized:** `loadGlyph`
  slices `abs(pitch) * rows` bytes from the bitmap's buffer pointer. The
  `pitch < 0` reordering exists on the package's `main` branch but **not in the
  released 2.14.3 dist** (verified in the installed package), so a bottom-up
  bitmap arrives bottom-up. The web actual honors the sign itself, exactly as
  the JVM actual does, and the two backends share one contract.
- `loadGlyph`'s defaults already are decision 5 — `LOAD_DEFAULT`, render on,
  `RENDER_MODE_NORMAL`.

## Decision 4 — the atlas: multi-chart, shelf-packed, `Font`-owned

- `Font` owns a per-`sizePx` cache; an atlas is created lazily at the first
  rasterization of that size and closed by `Font.close()` (LIFO with the face).
- Charts are `512×512` `Sprite`s (`SpriteService.create`, `SampleMode.NORMAL`,
  named for leak/fail-fast messages). Each chart packs shelf rows: a row is
  created at its first glyph's height, and a glyph takes the first row that fits
  it in both dimensions; otherwise a new row; otherwise a new chart.
  Deterministic placement, fully testable on the CPU — no GPU needed in D.
- Glyph pixels: opaque white RGB with alpha = coverage (transparent where 0), as
  in `fontDevelopment` and `C7`'s font sheet. The atlas is therefore
  tint- and mode-agnostic; one sheet serves every color.
- Empty glyphs (e.g. space) consume nothing — no entry, no growth — while the
  shaped advance still applies.
- Entry `AtlasGlyph(chartIndex, source, size, bearing)`: everything a blit needs,
  no advance.
- A glyph larger than a chart fails fast with a clear message; 512 px covers
  every size in scope.
- Single-threaded like the rest of the engine: no synchronization; the atlas
  mutates on the caller's thread (E1 confinement).
- The atlas assumes 1:1 NEAREST sampling by the future decal; padding for scaled
  sampling is out of scope.
- **No public API.** The atlas and the raster stay `internal`, consumed by E
  inside the module and pinned by module tests through internal accessors (the
  `fontSheet`/`fontDecal` precedent in `DrawStringService`).
- **The `ResourceScope` registration lands in D (owner, 2026-09-22).** Round C's
  micro-plan said "the atlas and the `ResourceScope` registration of `Font` land
  in D"; that stands, including the registration. D introduces the module's
  C7-style capability service, and the `Font` keeps ownership of its atlases —
  the shape and the rejected alternatives are decision 6.

## Decision 5 — raster settings and the parity contract

- Both backends: `FT_LOAD_DEFAULT` (hinting on) + `FT_RENDER_MODE_NORMAL`;
  `FT_Set_Pixel_Sizes(face, 0, sizePx)`, re-set only when the size changes. One
  `sizePx` drives both HarfBuzz's 26.6 scale and FreeType's pixel size.
- The pinned raster values are a **cross-build claim tied to FreeType 2.14.3 on
  both sides**: a version bump on either target must re-measure, as round C
  recorded for HarfBuzz 14.3.0 × 14.4.0.
- No public hinting/rendering-mode parameter: a parameter with no consumer, and
  the touch-point lists hinting-policy changes as deferred.
- Variable fonts stay on the **default instance**; D makes no variation calls.
  The recorded forward shape stands: when weight selection lands, HarfBuzz
  variations and `FT_Set_Var_Design_Coordinates` are set together, one `Font`
  per instance.

## Decision 6 — the capability service and scope ownership (owner, 2026-09-22)

Round C's micro-plan said the `ResourceScope` registration of `Font` lands in D;
the owner confirmed it and settled the shape.

- **The service adopts a loaded `Font`; the `Font` owns its atlases.**
  D introduces the module's `KGEOverridable` capability service (C7's
  `DrawStringService` is the precedent) with `createResources(scope, font)`,
  which registers the `Font` in the caller's scope under a private key. The
  scope owns and closes it; `Font.close()` closes the face and every per-size
  atlas. The service stays stateless and the `Font` remains a complete
  standalone resource, so `Font.load(...).use` — round C's tests and non-engine
  users — is untouched.
- **E's draws take the `Font` directly**, not the scope: `drawString(font, …)`
  through the same service facade. The scope's role is ownership, one scope can
  own several fonts, and no `Key` becomes public. Rejected: C7's literal shape
  (draws resolve the font from the scope through an internal key), which ties one
  font to one scope; and a public text handle returned by `createResources`,
  which adds a public type with no consumer.
- **Rejected B** (a `FontResources` holder owning the `Font` and the atlases): a
  second lifetime to reason about, and a standalone `Font` would stop
  rasterizing by itself.
- D ships the **extension-contract proof** principle 1 requires: an overriding
  decorator changes which `Font` the scope owns (observable through the scope),
  and the scope-close path closes it, so use after close fails fast.
- The name is settled at the micro-plan; `createResources` mirrors C7 even
  though it adopts instead of builds.

## Verification plan (the micro-plan's contract)

1. **Build.** FreeType moves to production: `lwjgl-freetype` (+ natives
   `runtimeOnly`) in `jvmMain`, the npm package and its externals moved from
   `webTest` to `webMain`, `kotlinx-coroutines-core` added to `webMain`.
   `kge-text-ttf:build` stays green with the round B/C smoke tests intact.
2. **Suspend load and the shared library.** Round C's `FontLoadTest`/
   `ShapingTest`/`MetricsTest`/`FontResourceTest`/`FontLeakReportTest` keep
   passing with the new signature; loading two fonts — concurrently on web —
   instantiates the library **once** and puts both faces in it.
3. **Raster constants** on the shipped Roboto 3.015 **default instance** at
   16 px, measured on JVM and both web targets first and pinned only once they
   agree (round C's procedure): per glyph `width × rows`, `bitmap_left`/
   `bitmap_top`, Σ coverage; an empty glyph (space) has no ink.
4. **Atlas.** Deterministic placement for a fixed glyph sequence; content (white
   RGB, alpha = coverage, transparent outside the ink); a repeated glyph reuses
   its entry (cache hit, same instance); per-size atlases; overflow opens a
   second chart; a glyph larger than a chart fails fast.
5. **Resource and scope.** `Font.close()` closes the atlases and their chart
   allocations (round C's leak/close tests extended); rasterizing after close
   fails fast through the existing guard; `createResources(scope, font)` makes
   the scope the owner, closing the scope closes the font and its atlases, and
   the extension-contract test proves an override changes what the scope owns.
6. **Ordering.** Shaping still produces round C's pinned run after a
   rasterization at the same and at a different size (the FT pixel size must not
   disturb the HarfBuzz scale).
7. **Gate.** `tools/gradle build` (every target incl. both browser suites,
   ktlint, `buildSrcCheck`); the `#35` zero-test guard makes the web counts
   trustworthy. Goldens are **not** wired into this module: the harness lives in
   `kge-core`'s test source set, so D pins raster numerically; a module-local
   golden harness is a follow-up, not a commitment.

## Carried to the micro-plan

- Exact names (`GlyphCoverage`, `AtlasGlyph`, `GlyphAtlas`, the seam method, the
  service) and the service method shape (`createResources` adopting).
- The `@zkl2333/freetype-wasm` externals (default export + named `FT` constants;
  the wasmJs `external object` form) and the shared-module init
  (`Deferred`/`Mutex`, one init per process), including how the single-init
  contract is observed in a test (an internal instantiation counter or module
  identity) and whether the concurrent-load case runs on web.
- Whether the non-gray pixel-mode guard gets a fixture (the module ships no
  bitmap-only font) or is recorded as an unguarded out-of-scope input.
- The chart-size constant and the oversized-glyph message.
- Whether the atlas exposes `charts` or a `chart(index)` accessor for E.

## Out of scope (round D)

CPU/decal blit, `drawString*`/`getTextSize*`, text addons, line breaking, tabs,
subpixel positioning, hinting-mode options, MONO and color-bitmap glyphs,
variable-font axes, padding for scaled sampling, the golden harness.
