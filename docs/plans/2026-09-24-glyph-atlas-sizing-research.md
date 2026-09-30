# Glyph atlas chart sizing — what the implementations and the specs actually say

**Date:** 2026-09-24. **Status:** research complete; **decision open** (§6 states
what follows and what is still unmeasured). Evidence only — nothing in the engine
changes here.

**Direction (owner, 2026-09-24).** The reduction is taken at the **upload seam**:
a one-channel coverage texture (`GL_R8` plus swizzle) with a region update, with
no `pixelStorei` and no CPU coverage surface — `GlyphAtlas`, its `Sprite` charts
and round D's white-alpha encoding stay as they are. `CHART_SIZE` is therefore
not what changes. The `R6` round `E` touch-point opens next session over this
direction and decides the proposed `E1` (CPU blit + addons) / `E2` (coverage
texture + decal) split.

## 0. The question, and why

`kge-text-ttf` caches rasterized glyphs in `512x512` `Sprite` charts
(`kge-text-ttf/.../GlyphAtlas.kt:132`, `private const val CHART_SIZE = 512`),
shelf-packed, one new chart whenever nothing fits, unbounded chart count. The
number 512 is inherited: the abandoned `fontDevelopment` branch had
`Configuration.glyphChartWidthHeight: Int = 512` guarded by
`check(value > 1 && (value and (value - 1)) == 0)` — "The chart dimension must be
positive and a power of 2" (`git show a867d1d:kge-core/src/main/kotlin/dev/staticsanches/kge/configuration/Configuration.kt`).
That mutable global was deleted during the port; the value stayed.

No measurement, citation or rationale for 512 exists anywhere in this repository.
This document answers three questions from primary sources — what real
implementations use, whether a power of two is required for KGE's exact use, and
what the number trades off — and then states what follows for the constant.

**Method.** Every implementation number below was read from the library's own
source or specification at a pinned tag/commit (fetched via `curl` from
`raw.githubusercontent.com` / `registry.khronos.org`), not from an article. Where
a claim could not be established, it is marked **unverified** rather than
asserted. Two spec PDFs were extracted with `pdftotext`; quotes are verbatim
including the PDF's own line-breaking artifacts.

## 1. What real implementations use

Each row states the page/chart dimension the implementation actually allocates,
what it does when that space runs out, and whether its source states a
power-of-two requirement.

| Implementation | Version / pinned ref | Page / chart dimension(s) | Growth on overflow | POT required? | Source |
|---|---|---|---|---|---|
| Dear ImGui `ImFontAtlas` | v1.91.5 (`f401021d`) | width heuristic `{4096, 2048, 1024, 512}` from `surface_sqrt`; height = packed extent rounded up to a power of two | none inside `Build()` — one pack pass into a fixed `TEX_HEIGHT_MAX = 1024 * 32` target; failure is a known FIXME | width POT by the heuristic, height POT by default with an opt-out flag; `TexDesiredWidth` doc says "Must be a power-of-two" | [imgui_draw.cpp](https://github.com/ocornut/imgui/blob/v1.91.5/imgui_draw.cpp#L2952-L2991), [imgui.h](https://github.com/ocornut/imgui/blob/v1.91.5/imgui.h#L3294-L3399) |
| Dear ImGui `ImFontAtlas` | v1.92.1 tag (1.92.0 rewrote the atlas) | starts **512x128**; `TexMinWidth=512`, `TexMinHeight=128`, `TexMaxWidth=TexMaxHeight=8192` | grows one texture by x2 (height before width), then repacks; retries 4 times, then returns `ImFontAtlasRectId_Invalid` | **yes** — `IM_ASSERT(ImIsPowerOfTwo(...))` on all four knobs; `NoPowerOfTwoHeight` can opt the height out in the size estimate | [imgui.h](https://github.com/ocornut/imgui/blob/v1.92.1/imgui.h#L3673-L3676), [imgui_draw.cpp](https://github.com/ocornut/imgui/blob/v1.92.1/imgui_draw.cpp#L4078-L4197) |
| Dear ImGui incremental updates | v1.92.1 | per-rectangle `ImTextureRect` updates | `ImTextureStatus_WantUpdates` + `ImTextureData::Updates[]`/`UpdateBox`; BACKENDS.md sample says "Upload a rectangle of pixels to the existing texture" | n/a | [imgui.h](https://github.com/ocornut/imgui/blob/v1.92.1/imgui.h#L3406-L3416), [BACKENDS.md](https://github.com/ocornut/imgui/blob/v1.92.1/docs/BACKENDS.md#L351-L362) |
| `stb_truetype` + `stb_rect_pack` | master `2c980bb5` (headers v1.26 / 1.01) | **caller-supplied** `stbtt_PackBegin(..., width, height, ...)`; the documented sample and the in-tree demo use 512x512, a disabled in-tree test uses 256x512, the active one 1024x1024 | returns 0 / sets `was_packed = 0`; caller decides | **no statement anywhere** | [stb_truetype.h](https://github.com/nothings/stb/blob/2c980bb59875b0d32144a71867fbdebb2f77cd20/stb_truetype.h#L588-L597), [stb_rect_pack.h](https://github.com/nothings/stb/blob/2c980bb59875b0d32144a71867fbdebb2f77cd20/stb_rect_pack.h#L90-L113) |
| Skia Ganesh glyph atlas (`GrDrawOpAtlasConfig`) | main `aac31b04` | ARGB/LCD from a 6-entry table driven by `maxBytes`: 256x256, 512x256, 512x512, 1024x512, 1024x1024, **2048x1024**; A8 = 2x those, clamped; plots 256x256 (512x512 for A8 at >=2048) | allocates another **same-size page**, up to `kMaxMultitexturePages = 4`; never a larger texture | table values are POT; `kMaxAtlasDim = 2048` is a *precision* cap, not a POT rule | [GrDrawOpAtlas.cpp](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/ganesh/GrDrawOpAtlas.cpp#L579-L630), [GrDrawOpAtlas.h](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/ganesh/GrDrawOpAtlas.h#L269-L295) |
| Skia Graphite glyph atlas (`TextAtlasManager::AtlasConfig`) | main `aac31b04` | identical table, identical `kMaxAtlasDim = 2048` | same: pages up to 4 (`DrawAtlas::kMaxMultitexturePages`) | same as Ganesh | [TextAtlasManager.h](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/graphite/text/TextAtlasManager.h#L49-L58) |
| Skia strike caches (not a texture) | main `aac31b04` | byte budget **2 MiB**, count budget **2048**, CPU and GPU caches | LRU eviction against the budget | n/a | [SkStrikeCache.h](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/core/SkStrikeCache.h#L29-L37) |
| NanoVG / fontstash | master `ce3bf745` | starts **512x512**; `NVG_MAX_FONTIMAGE_SIZE = 2048`, `NVG_MAX_FONTIMAGES = 4` | allocates the **next font image**, doubling the *shorter* side, clamps at 2048, then `fonsResetAtlas` (discards the old glyph cache) | no statement; sizes are POT | [nanovg.c](https://github.com/memononen/nanovg/blob/ce3bf745eb2d2dbc14a50bf2446783f691ac4353/src/nanovg.c#L40-L42) |
| `glyph_brush` 0.7.12 / `glyph_brush_draw_cache` 0.1.6 | `fc70546` / `9da30ea` | **default 256x256** (`DrawCacheBuilder::default()`) | does **not** grow: returns `BrushError::TextureTooSmall { suggested: (w*2, h*2) }` | no | [draw-cache lib.rs](https://github.com/alexheretic/glyph-brush/blob/9da30eacb54534248f62743f8e5a171694e529e1/draw-cache/src/lib.rs#L198-L208) |
| `glyphon` 0.12.0 | `7f43114` | **`InnerAtlas::INITIAL_SIZE = 256`**; two atlases (color + mask) | grows by `GROWTH_FACTOR = 2` per dimension, capped at `device.limits().max_texture_dimension_2d` | no — the cap is the device limit, not a POT rule | [text_atlas.rs](https://github.com/grovesNL/glyphon/blob/7f43114dd17bfe15916127d4132ae3b981f3d5b3/src/text_atlas.rs#L30-L35) |
| `cosmic-text` 0.19.0 | `c24886c` | **no atlas and no texture** (`SwashCache` is a `HashMap<CacheKey, SwashImage>`) | n/a — the renderer owns packing | n/a | [swash.rs](https://github.com/pop-os/cosmic-text/blob/c24886c2471e5606587c46090cd25dbbf209186b/src/swash.rs#L131-L152) |
| `fontdue` 0.9.4 | `b74746f` | **no packed atlas**; one bitmap per `rasterize()` call | n/a | n/a | [font.rs](https://github.com/mooman219/fontdue/blob/b74746f111056dba7c30162b18403dd6b6f7edbd/src/font.rs#L509-L529) |
| `rusttype` 0.9.3 `gpu_cache` | `99ee967` | **default `dimensions: (256, 256)`** | dimensions are immutable by design; fullness is `CacheWriteErr::GlyphTooLarge` / `NoRoomForWholeQueue`, caller rebuilds | no | [gpu_cache.rs](https://github.com/redox-os/rusttype/blob/99ee9676e3143337806a4e4aab6ece1fad04ae82/src/gpu_cache.rs#L244-L253) |
| Unity TextMeshPro — Font Asset Creator | 3.2.0-pre.15 (`51da8a44`) | **window default 512x512**; dropdown `8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192` | window does not create multi-atlas assets | dropdown is POT; no requirement stated | [TMPro_FontAssetCreatorWindow.cs](https://github.com/needle-mirror/com.unity.textmeshpro/blob/3.2.0-pre.15/Scripts/Editor/TMPro_FontAssetCreatorWindow.cs) |
| Unity TextMeshPro — create menu / runtime API | 3.2.0-pre.15 | **1024x1024**, with the source comment `// Default atlas resolution is 1024 x 1024.` and `CreateFontAsset(font, 90, 9, GlyphRenderMode.SDFAA, 1024, 1024)` | `isMultiAtlasTexturesEnabled` (default `false`; the runtime path sets `true`) allocates additional `m_AtlasWidth x m_AtlasHeight` pages in a `while` loop; array doubles, index only increments | no requirement stated; values are POT | [TMP_FontAsset_CreationMenu.cs](https://github.com/needle-mirror/com.unity.textmeshpro/blob/3.2.0-pre.15/Scripts/Editor/TMP_FontAsset_CreationMenu.cs#L169-L173), [TMP_FontAsset.cs](https://github.com/needle-mirror/com.unity.textmeshpro/blob/3.2.0-pre.15/Scripts/Runtime/TMP_FontAsset.cs#L538-L541) |
| Unity TMP — unified docs | TMP 3.2.0-pre.15 | Atlas Resolution doc: "A resolution of 512 x 512 is fine for most fonts, as long as you are only including ASCII characters. Fonts with more characters may require larger resolutions, or multiple atlases." | docs recommend splitting into multiple font assets | — | [FontAssetsCreator.html](https://docs.unity3d.com/Packages/com.unity.textmeshpro@3.2/manual/FontAssetsCreator.html) |
| `msdf-atlas-gen` | master `6148900` (v1.4.0) | **no default**; `-dimensions <w> <h>` sets it, or a constraint picks the minimum | **single atlas**: `Error: Could not fit N out of M glyphs into the atlas.` and exit 1 | `-pots` (POT square) / `-potr` (POT square-or-rectangle) are *options*; the default constraint is `-square4` (side divisible by 4) | [main.cpp](https://github.com/Chlumsky/msdf-atlas-gen/blob/6148900d59423059bafde2f51a0cb303184404bd/msdf-atlas-gen/main.cpp#L96-L101), [README](https://github.com/Chlumsky/msdf-atlas-gen/blob/6148900d59423059bafde2f51a0cb303184404bd/README.md#L93-L103) |
| Godot `TextServerAdvanced` (shelf packer) | 4.3-stable `77dcf97d` | per (font, size) page = `next_power_of_2(MAX(size.x * oversampling * 8, 256))`, clamped `<= 1024` (normal) / `<= 2048` (MSDF); raised to fit a single oversized glyph. Default-constructed `ShelfPackTexture` is 1024x1024 but the packer never uses that default. | `p_data->textures.push_back(tex)` — another page, no page cap; `pack_rect` returns `(-1,0,0)` when nothing fits | **page side is `next_power_of_2`**; no rationale comment | [text_server_adv.cpp](https://github.com/godotengine/godot/blob/4.3-stable/modules/text_server_adv/text_server_adv.cpp#L833-L873), [text_server_adv.h](https://github.com/godotengine/godot/blob/4.3-stable/modules/text_server_adv/text_server_adv.h#L163-L224) |
| **KGE (today)** | working tree | fixed **512x512** RGBA8, shelf-packed | new chart (unbounded) | assumed, never justified | `kge-text-ttf/.../GlyphAtlas.kt` |

Two shape facts fall out of the table:

- **No implementation uses a fixed chart size as an invariant.** Every one of
  them either takes the dimension from the caller (`stb_truetype`, `msdf-atlas-gen`),
  computes it from the glyph set / a byte budget (ImGui, Skia, Godot), starts
  small and grows (glyphon, NanoVG, TMP multi-atlas), or refuses and asks the
  caller to resize (`glyph_brush`, `rusttype`). KGE is the odd one out in pinning
  a constant.
- The **most common default page size in this survey is 256** (glyph_brush,
  glyphon, rusttype: 256x256; Skia ARGB plots: 256x256; Godot floor: 256).
  **512 appears as a floor/initial/most-common-choice** (ImGui v1.91.5 width
  floor and v1.92 `TexMinWidth`; NanoVG initial; TMP Creator window default;
  stb's own 512x512 samples). 1024 and 2048 appear as *caps* (ImGui's 8192,
  Skia's `kMaxAtlasDim`, NanoVG/Godot clamps), as user-selected values, as TMP's
  programmatic create-menu default, and as Skia's *derived* default atlas size —
  2048x1024 ARGB / 2048x2048 A8 at the 8 MiB `fGlyphCacheTextureMaximumBytes`
  default (that size is **derived arithmetic** from the quoted dimension table,
  not a literal source line).

## 2. Spec findings

KGE's two backends are exactly two specs: JVM requests an **OpenGL 3.3 core**
context (`DriverServiceJvm.kt:38-41`) and web creates a **WebGL2** context
(`DriverServiceWeb.kt:100`, `WebGL2RenderingContext.ID`). Both are quoted below.

### 2.1 Non-power-of-two textures are legal in both backing specs

OpenGL 3.3 core, Appendix J.3.29:

> The name string for non-power-of-two textures is `GL_ARB_texture_non_power_of_two`.
> It was promoted to a core feature in OpenGL 2.0.

The phrase "power of two" occurs in the whole GL 3.3 core specification body only
in that appendix, in the rectangular-textures appendix (J.3.33), and once about
RGTC *compressed* blocks (Appendix G.4: "so that non-power-of-two RGTC images may
be specified"). There is **no power-of-two requirement on uncompressed 2D texture
dimensions** anywhere in the normative text.

OpenGL ES 3.0.6, Appendix E.1 ("New Features"), lists among what 3.0 adds over
ES 2.0:

> non-power-of-two textures with full wrap mode support and mipmapping

and defines mipmap level counts for arbitrary dimensions (§3.8.10.4):

> If the image array of level `levelbase` has dimensions `wt x ht x dt`, then
> there are `floor(log2(maxsize)) + 1` levels in the mipmap

with level `i` at `max(1, w/2^i) x max(1, h/2^i)`. NPOT is first-class in ES 3.0,
including mipmaps and every wrap mode.

WebGL 2.0, §"Other differences Between WebGL 2.0 and WebGL 1.0" →
"Non-Power-of-Two Texture Access":

> Texture access works in the WebGL 2.0 API as in the OpenGL ES 3.0 API. In other
> words, unlike the WebGL 1.0 API, there are no special restrictions on non power
> of 2 textures. All mipmapping and all wrapping modes are supported for
> non-power-of-two images.

The WebGL 2.0 spec states it "conforms closely to the OpenGL ES 3.0 API" (its
scope section) and adds no POT restriction.

### 2.2 Minimum guaranteed `MAX_TEXTURE_SIZE`

From the state tables — the column heading is literally **"Minimum Value"**, i.e.
the value every conforming implementation must support at least:

| Spec | Table | `MAX_TEXTURE_SIZE` minimum | Verbatim description |
|---|---|---|---|
| OpenGL 3.3 core | Table 6.38, Implementation Dependent Values | **1024** | "Maximum 2D/1D texture image dimension" |
| OpenGL ES 3.0.6 | Table 6.28, Implementation Dependent Values | **2048** | "Maximum 2D texture image dimension" |

WebGL 2.0 does not restate a numeric minimum (a full-text search of the spec
finds no `MAX_TEXTURE_SIZE` capability table); it inherits GLES 3.0's **2048**
minimum by construction — this is *inherited*, not restated, and is marked
accordingly.

Consequence for candidate sizes: **256, 512 and 1024 are guaranteed on both
backends. 2048 is guaranteed on WebGL2 but exceeds the GL 3.3 core minimum of
1024**, so a 2048 chart on the JVM would rely on the actual device limit and
would need a runtime `GL_MAX_TEXTURE_SIZE` query. No such query exists in this
repository today (`grep -rn MAX_TEXTURE_SIZE --include=*.kt .` returns nothing).

### 2.3 When a power of two actually matters

For KGE's specific sampler configuration — a 2D texture, `NEAREST` magnification
and minification, `CLAMP_TO_EDGE`, no mipmaps, no `REPEAT` — none of the classic
POT requirements apply:

| Operation | POT needed? | Basis |
|---|---|---|
| Sampling with NEAREST + CLAMP_TO_EDGE, no mipmaps | **No** | GL 3.3 J.3.29; GLES 3.0 Appendix E.1; WebGL 2.0 NPOT section. This is also the *only* NPOT configuration GLES 2.0 ever allowed (see below), so it is the universally safe case. |
| Mipmapping | No in GL 3.3 / GLES 3.0 / WebGL 2 | GLES 3.0 §3.8.10.4 defines NPOT mip chains; WebGL 2.0 lifts "all mipmapping" restrictions. (It *was* required in WebGL 1 / GLES 2 — historical, not KGE's target.) |
| `REPEAT` wrap | No in GLES 3.0 / WebGL 2 | WebGL 2.0 NPOT section: "all wrapping modes are supported for non-power-of-two images". KGE uses CLAMP_TO_EDGE regardless. |
| Row alignment (`GL_UNPACK_ALIGNMENT`) | No | Default value 4 (GL 3.3 §3.7.1 Table 3.1; GLES 3.0 §3.7.1). The row stride is the group count rounded up to a multiple of the alignment. KGE's sprites are RGBA8 — 4 bytes per pixel, so every row is already a multiple of 4 and the alignment rule never inserts padding. |
| Compressed texture block alignment | Not applicable | KGE uploads uncompressed RGBA8; compressed formats are out of scope and are not covered here. |
| Driver "fast path" / tiling / swizzle | **unverified** | No primary source found that requires or documents a POT fast path for this use. Treat the "drivers are faster with POT" argument as unestablished, not as fact. |

The historical restriction, for contrast — OpenGL ES 2.0.25 §3.8.2 ("Shader
Execution" → "Texture Access") lists among the conditions under which a texture
lookup is incomplete:

> A two-dimensional sampler is called, the corresponding texture image is a
> non-power-of-two image (as described in the Mipmapping discussion of section
> 3.7.7), and either the texture wrap mode is not CLAMP_TO_EDGE, or the
> minification filter is neither NEAREST nor LINEAR.

That is, ES 2.0/WebGL 1 permitted NPOT only with exactly `CLAMP_TO_EDGE` +
`NEAREST`/`LINEAR` and no mipmaps — KGE's configuration. ES 3.0 removed the
restriction entirely (§2.1), and WebGL 2.0 inherits the removal.

### 2.4 In-repo proof that NPOT already works here

The existing bitmap-font path uploads a **128x48** sheet — 48 is not a power of
two — and samples it `NEAREST` / `CLAMP_TO_EDGE`
(`DrawStringService.kt:276-277`, `287`), on both backends, and it is covered by
the green test suites. So NPOT is not merely legal on paper for KGE: it is
already exercised on JVM GL 3.3 core and WebGL2.

## 3. The float-UV precision claim

The widely repeated argument is that a power-of-two size makes `pixel / size`
exactly representable in a float, so UVs like `1/size` are exact. **That specific
claim was not found in any primary source examined** — not in `imgui.h`/
`imgui_draw.cpp` (1.91.5 or 1.92.1), `stb_truetype.h`, `stb_rect_pack.h`,
`fontstash.h`, `nanovg.c`, `nanovg_gl.h`, or the Godot/MSDF/TMP sources. It is
recorded here as **folklore**: unverified, and not usable as a justification.

What *does* exist is a related, different, and citable primary source. Skia caps
its glyph atlas dimensions with an explicit floating-point-precision rationale
(`GrDrawOpAtlas.h`, restated verbatim in Graphite's `TextAtlasManager.h`):

> // On some systems texture coordinates are represented using half-precision floating point
> // with 11 significant bits, which limits the largest atlas dimensions to 2048x2048.
> // For simplicity we'll use this constraint for all of our atlas textures.
> // This can be revisited later if we need larger atlases.
> inline static constexpr int kMaxAtlasDim = 2048;

This is a limit on the **magnitude** of a texture coordinate, not a requirement
that a dimension be a power of two: 2048 is exactly representable in 11
significant bits, and so is 1536. It supports one honest conclusion — **very
large atlases are risky because of texcoord precision** — and it argues for a
cap, not for POT. No implementation examined documents a reason for the POT
assertions it does make: ImGui 1.92 asserts `ImIsPowerOfTwo` on its four texture
knobs and ImGui 1.91.5 says `TexDesiredWidth` "Must be a power-of-two", with no
stated rationale; `glyph_brush`, `glyphon`, `rusttype`, `stb_truetype`,
`cosmic-text` and `fontdue` state no POT rule at all.

## 4. Cost model

### 4.1 Hard numbers per candidate chart size

| Chart side | Bytes per chart (`W*H*4`, RGBA8) | Max single-glyph box | Full chart re-upload | Largest pinned 32px glyph (21x23) as a partial update |
|---|---|---|---|---|
| 256 | 262,144 (256 KiB) | 256x256 | 256 KiB | 1,932 B |
| 512 | 1,048,576 (**1 MiB**) | 512x512 | 1 MiB | 1,932 B |
| 1024 | 4,194,304 (4 MiB) | 1024x1024 | 4 MiB | 1,932 B |
| 2048 | 16,777,216 (16 MiB) | 2048x2048 | 16 MiB | 1,932 B |

The chart side is simultaneously **the granularity of growth** and **the hard cap
on a single glyph box** — `GlyphAtlas.place` fails fast when a coverage box
exceeds `CHART_SIZE` (`GlyphAtlas.kt:51-53`). At the current 1:1 NEAREST design
the box tracks the requested pixel size (the pinned 32px glyphs reach 21 wide and
25 tall; the 16px ones 11x12), so 256 supports roughly up to a 256px em size and
512 up to 512px, with a small margin for the hinted box exceeding the em size.

### 4.2 Capacity, using the pinned Roboto 3.015 metrics

Box areas are the pinned `width x rows` values from
`docs/decisions/phase-1/36-kge-text-ttf-round-d.md` (do not re-measure): at 16px
the six glyphs average `97.5 px^2` (`585/6`); at 32px `363.3 px^2` (`2180/6`).

Exact shelf-grid capacity for the largest pinned 32px glyph (`A`, 21x23), i.e.
"how many of the widest/tallest glyphs fit if the chart is filled with it":

| Chart side | rows x columns | glyphs per chart | grid utilization |
|---|---|---|---|
| 256 | `floor(256/23) x floor(256/21)` = 11 x 12 | **132** | 97.3% |
| 512 | 22 x 24 | **528** | 97.3% |
| 1024 | 44 x 48 | **2112** | 97.3% |
| 2048 | 89 x 97 | **8633** | 99.4% |

Charts needed for whole glyph sets (perfect-packing lower bound `ceil(area / side^2)`,
with a 50% shelf-efficiency column as sensitivity — shelf packing wastes the tail
of every row, so the true value sits between the two):

| Set | Total box area | 256 | 512 | 1024 | 2048 |
|---|---|---|---|---|---|
| ASCII 95 glyphs @16px | 9,263 px^2 | 1 (1) | 1 (1) | 1 (1) | 1 (1) |
| Latin-1 191 glyphs @32px | 69,397 px^2 | 2 (3) | 1 (1) | 1 (1) | 1 (1) |
| CJK 2000 glyphs @16px | 195,000 px^2 | 3 (6) | 1 (2) | 1 (1) | 1 (1) |

(parenthesised = at 50% shelf efficiency. All three sets are **estimates** from
the six pinned boxes — the per-glyph average is the only measured input, and the
CJK row additionally extrapolates a Latin box area; only the capacity table above
is exact.)

The practical reading: **for Latin-scale sets, 256 suffices for one size** — 512
starts to pay only for large sets at large sizes or for very large glyphs. Since
KGE builds **one atlas per `sizePx`** (`Font.kt:50-51`), the per-size cost is the
real driver:

| Distinct sizes in use, ASCII-only sets | 256 | 512 | 1024 | 2048 |
|---|---|---|---|---|
| 1 size | 256 KiB | 1 MiB | 4 MiB | 16 MiB |
| 4 sizes | 1 MiB | 4 MiB | 16 MiB | 64 MiB |

A structural factor here: the charts are **RGBA8, 4 bytes per pixel**
(`writeCoverage` stores `Pixel.rgba(255,255,255,alpha)` into a `Sprite` of
`width*height*Int.SIZE_BYTES`, and `Texture.create`/`update` use `GL.RGBA`), even
though only one channel carries information. The comparable industry examples
store the same white+alpha content as **1 byte per pixel**: NanoVG's font image is
created as `NVG_TEXTURE_ALPHA` (`nanovg.c:333`), ImGui defaults `TexDesiredFormat`
to `ImTextureFormat_RGBA32` but offers `ImTextureFormat_Alpha8`, and TMP's 3.0.7
creator allocates `TextureFormat.Alpha8`. So KGE's 512 chart is **4x heavier than
the industry's 512x512 examples** (1 MiB vs 256 KiB). Changing the format is a
separate concern (it needs a shader/swizzle change) and is out of scope here, but
it is the largest single lever on atlas memory and belongs in the same decision.

### 4.3 Upload cost — and the current full-re-upload obligation

Because KGE's atlas grows lazily *after* a chart may already have been uploaded
(the GPU round lands later), the cost of "one more glyph" is what matters:

- Whole-chart re-specify: `W*H*4` bytes — 256 KiB / 1 MiB / 4 MiB / 16 MiB.
  At 512, that is **1 MiB copied per newly added glyph after upload**.
- Partial `glTexSubImage2D` of one 32px glyph box: `21*23*4 = 1,932 B`, i.e.
  **543x less than a 512 chart** and 136x less than a 256 chart.

This is not hypothetical: ImGui 1.92 added exactly this protocol
(`ImTextureStatus_WantUpdates`, "Upload a rectangle of pixels to the existing
texture"), NanoVG uploads only the dirty rect through
`glTexSubImage2D` (`nanovg_gl.h:854-861`), and Godot's page API is a per-page
image (`font_set_texture_image`). Whole-texture re-upload on every new glyph is
the outlier.

### 4.4 Binds / draw batches

Each chart is a separate texture, so a frame touching `k` charts needs `k`
`bindTexture` calls and potentially `k` draw batches. The repo's own measurement
apparatus priced texture binds: in `docs/plans/2026-09-20-renderer-lever-measurements.md`
§3.3, deduplicating `bindTexture` among the state levers moved the text cell by
-23.6% (JVM) / -14.7% (web) — but that measurement dedupes **one bind per glyph**.
For a paged atlas the count is per *chart switch within a frame*, bounded by the
chart count (1 for a Latin set at any candidate size; 2-3 before a CJK set needs
more). A larger chart therefore buys at most a handful of binds per frame, while
costing memory linearly. No measurement in the repo covers the atlas case; the
existing lever apparatus could.

## 5. Repo cross-check: can the GL seam do a partial texture update?

**The seam can; the production resource wrappers do not call it.**

`GLService` declares `texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, srcData)`
(`kge-core/.../gl/service/GLService.kt:69-80`), implemented by
`LwjglGLService.kt:56-66` (`GL33.glTexSubImage2D`) and `WebGLService.kt:82-92`
(`gl.texSubImage2D`), and forwarded by the delegating `Proxy`
(`GLService.kt:298-308`). So both backends already expose a partial upload.

But the only other reference in the tree is the test recorder
(`commonTest/.../RecordingGLService.kt:84`) — there is **no production caller**:

- `Decal.update()` → `Renderer.updateTexture(texture, sprite)`
  (`Decal.kt:52`, `Renderer.kt:51`, `DefaultRenderer.kt:47-50`)
  → `Texture.update(sprite)` (`Texture.kt:33-46`)
  → `GL.texImage2D(GL.TEXTURE_2D, 0, GL.RGBA, sprite.width, sprite.height, 0, GL.RGBA, GL.UNSIGNED_BYTE, sprite.buffer)`
  — a **full re-specification** of the whole surface.
- `Decal.invoke` (`Decal.kt:94-105`) and `Texture.create` (`Texture.kt:76-109`,
  `texImage2D` with `null` at line 96) are the allocation paths; neither has a
  region variant.

So today: **adding a glyph to an already-uploaded chart requires a full
`glTexImage2D` re-upload of `W*H*4` bytes** — 1 MiB per new glyph at 512. Reaching
partial uploads needs a new production consumer of `texSubImage2D` (a
region-update factory/method on `Texture`, and a way for the atlas/`Font` to know
the glyph's chart rectangle on the GPU side). That is a seam addition, not a
change to the packing logic.

Two further repo facts relevant to the decision:

- **No runtime texture-limit check exists.** `grep -rn MAX_TEXTURE_SIZE --include=*.kt .`
  is empty, so a chart side above the GL 3.3 minimum (1024) would fail at
  `Texture.create` rather than being clamped or reported deliberately.
- **NPOT is already exercised:** the bitmap font's 128x48 sheet
  (`DrawStringService.kt:276-277`) is uploaded with `Decal.Filter.NEAREST` and
  `Decal.Wrap.CLAMP_TO_EDGE` (`DrawStringService.kt:287`) and works on both
  backends.

## 6. What this implies for KGE's 512

**Is 512 defensible on industry grounds?** Partly, and only as a *starting page
size*.

- For it: 512 is a legitimate, common starting page. ImGui's width floor and
  `TexMinWidth` are 512, NanoVG's initial font image is 512x512, TMP's Creator
  window defaults to 512x512, and stb's own samples use 512x512. An atlas that
  starts at 512 is not out of line.
- Against it: **no implementation in the survey fixes the chart size as a
  constant.** They take a caller value, compute one from the glyph set or a byte
  budget, or grow. And KGE's 512 chart is a *quarter-megabyte-times-four*: because
  it is RGBA8, it costs 1 MiB where the industry's 512 alpha atlases cost
  256 KiB, and KGE pays that per `sizePx` even when that size draws five glyphs.
- The original justification — a global mutable knob on an abandoned branch with
  a power-of-two `check` — is not evidence. Section 2.3 shows the power-of-two
  part of that check buys nothing for this sampler configuration, and §2.4 shows
  the repo already runs NPOT fine.

**Is there a better-supported value?** The evidence supports a **range, not a
single number**:

- **256 is the best-supported first-chart size.** It is the default page size of
  glyph_brush, glyphon and rusttype, the Skia ARGB plot size, and (as a computed
  minimum) Godot's floor. It costs 256 KiB per size, holds every ASCII/Latin set
  at 16-32px in one chart (§4.2), and 132 of the largest 32px glyphs.
- **512 is defensible too, and is the better choice if the first chart is expected
  to absorb a whole large set (Latin-1+ at >=32px, or a multi-script set) without
  a second chart.** It is the largest *default* first page found in the survey.
- **1024 and 2048 as a per-size *first* chart are the weakest-supported
  option.** Skia does allocate a 2048-scale atlas by default (the derived
  2048x1024 ARGB / 2048x2048 A8 row) and TMP's programmatic create-menu default
  is 1024x1024 — but both are one *global* atlas for the whole application,
  sized by a byte budget or by SDF quality, not one page per `sizePx`. In ImGui,
  NanoVG and Godot, 1024/2048 are caps or byte-budget outcomes. At KGE's
  per-size granularity they cost 4-16 MiB per size, and 2048 exceeds the GL 3.3
  guaranteed minimum (§2.2).
- One hard floor remains: **the chart side must be >= the largest glyph box the
  engine must support**, because it is the fail-fast cap (`GlyphAtlas.kt:51-53`).
  A 256 chart cannot rasterize a 512px glyph, and there is no size cap in
  `Font.glyph` other than `sizePx > 0` (`Font.kt:47-51`).

**Evidence still missing.** A defensible constant cannot be picked from this
document alone, because the survey says the right size is a function of the
glyph set, the sizes in use and the upload policy — none of which are measured
here:

1. The real per-size glyph sets and box areas for the sizes the engine will use
   (the pinned table has six glyphs at two sizes; §4.2 extrapolates from it).
2. Whether a chart is ever uploaded before it is complete — i.e. whether the
   full-re-upload cost in §4.3 is paid once per chart or once per glyph.
3. How many distinct `sizePx` values a real app creates (memory scales with the
   product of the two).
4. `GL_MAX_TEXTURE_SIZE` on the target devices, since no check exists and 2048
   is not guaranteed by GL 3.3 core.

**What the follow-up round should measure rather than assume.**

- Drive the existing `GlyphAtlas` logic (it is pure — the rasterizer is a
  constructor parameter, `GlyphAtlas.kt:28-31`) over the **real** glyph set for
  each size the engine ships, instrumenting chart count and per-chart occupancy
  for 256/512/1024, so the choice rests on measured occupancy instead of the
  bounded estimate in §4.2.
- Measure the upload policy on both backends with the `kge-benchmark` apparatus:
  cost of a full `glTexImage2D` chart re-upload versus a `glTexSubImage2D`
  glyph-box update at 256/512, on JVM GL 3.3 core and WebGL2, at the same rigor
  as the renderer-lever round (real GPU, uncapped, control cell).
- Measure the *chart-count* effect on a realistic frame (binds/draw batches as a
  function of page count), since that is the only thing a larger first chart
  buys.
- Query `GL_MAX_TEXTURE_SIZE` at context creation and record it, so any future
  chart side above 1024 is a checked decision rather than a latent
  `Texture.create` failure.

**Interim verdict (evidence-bounded).** 512 is *defensible but unexplained*, and
the power-of-two part of its ancestry is not required. If the round must pick a
number before measuring, the survey supports **256 as the first-chart size with
growth by adding 256 charts**, or **512 if the first chart must hold a
Latin-1-scale set at >=32px in one page** — with the chart side never below the
largest glyph box the engine promises to support. What the survey does *not*
support is keeping 512 as an unquestioned constant, or moving to 1024/2048 as a
default.

## 7. Sources

Specifications:

- [OpenGL 3.3 (Core Profile) Specification](https://registry.khronos.org/OpenGL/specs/gl/glspec33.core.pdf)
  — Appendix J.3.29 (NPOT promoted to core in 2.0), J.3.33, G.4; Table 6.38
  (`MAX_TEXTURE_SIZE` minimum 1024); §3.7.1 Table 3.1 (`UNPACK_ALIGNMENT`
  default 4).
- [OpenGL ES 3.0.6 Specification](https://registry.khronos.org/OpenGL/specs/es/3.0/es_spec_3.0.pdf)
  — Appendix E.1 (NPOT with full wrap and mipmapping is new in 3.0); §3.8.10.4
  (NPOT mipmap level count); Table 6.28 (`MAX_TEXTURE_SIZE` minimum 2048).
- [OpenGL ES 2.0.25 Full Specification](https://registry.khronos.org/OpenGL/specs/es/2.0/es_full_spec_2.0.pdf)
  — §3.7.10 and §3.8.2 "Texture Access": the historical NPOT restriction to
  `CLAMP_TO_EDGE` + `NEAREST`/`LINEAR` (the configuration KGE uses).
- [WebGL 2.0 Specification](https://registry.khronos.org/webgl/specs/latest/2.0/)
  — §"Other differences Between WebGL 2.0 and WebGL 1.0" → "Non-Power-of-Two
  Texture Access" (the NPOT quote); scope section (conforms closely to OpenGL ES
  3.0, hence the inherited 2048 minimum).

Implementations:

- [Dear ImGui v1.91.5 `imgui_draw.cpp`](https://github.com/ocornut/imgui/blob/v1.91.5/imgui_draw.cpp#L2952-L2991)
  — width heuristic floor 512, `TEX_HEIGHT_MAX`, `ImUpperPowerOfTwo`, the
  unhandled packing failure FIXME.
- [Dear ImGui v1.91.5 `imgui.h`](https://github.com/ocornut/imgui/blob/v1.91.5/imgui.h#L3294-L3399)
  — `TexGlyphPadding = 1` doc, `NoPowerOfTwoHeight`, `TexDesiredWidth` "Must be a
  power-of-two", `TexUvScale = (1.0f/TexWidth, 1.0f/TexHeight)`.
- [Dear ImGui v1.92.1 `imgui.h`](https://github.com/ocornut/imgui/blob/v1.92.1/imgui.h#L3406-L3416)
  and [`imgui_draw.cpp`](https://github.com/ocornut/imgui/blob/v1.92.1/imgui_draw.cpp#L4078-L4197)
  — `TexMinWidth=512`, `TexMinHeight=128`, `TexMaxWidth=TexMaxHeight=8192`, the
  `ImIsPowerOfTwo` asserts, x2 growth with repack, the incremental-update structs.
- [Dear ImGui v1.92.1 `docs/BACKENDS.md`](https://github.com/ocornut/imgui/blob/v1.92.1/docs/BACKENDS.md#L351-L362)
  — the backend contract for rectangle updates to an existing texture.
- [`nothings/stb` master `2c980bb5` `stb_truetype.h`](https://github.com/nothings/stb/blob/2c980bb59875b0d32144a71867fbdebb2f77cd20/stb_truetype.h#L588-L597)
  — `stbtt_PackBegin`'s documented (size-free) constraints, padding 1.
- [`nothings/stb` master `2c980bb5` `stb_rect_pack.h`](https://github.com/nothings/stb/blob/2c980bb59875b0d32144a71867fbdebb2f77cd20/stb_rect_pack.h#L90-L113)
  — overflow sets `was_packed = 0` and returns 0; `stbrp_init_target` node rules.
- [`nothings/stb` master `2c980bb5` `tests/oversample/main.c`](https://github.com/nothings/stb/blob/2c980bb59875b0d32144a71867fbdebb2f77cd20/tests/oversample/main.c)
  — the in-tree 512x512 caller; `tests/test_truetype.c` for the 1024x1024 and
  256x512 callers.
- [Skia `main` `aac31b04` `src/gpu/ganesh/GrDrawOpAtlas.h`](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/ganesh/GrDrawOpAtlas.h#L269-L295)
  — the half-precision/11-significant-bits rationale and `kMaxAtlasDim = 2048`.
- [Skia `main` `aac31b04` `src/gpu/ganesh/GrDrawOpAtlas.cpp`](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/ganesh/GrDrawOpAtlas.cpp#L579-L630)
  — the ARGB dimension table, `maxBytes` indexing, plot dimensions, padding rule.
- [Skia `main` `aac31b04` `src/gpu/ganesh/GrAtlasTypes.h`](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/ganesh/GrAtlasTypes.h#L113-L129)
  — `kMaxMultitexturePages = 4`, `kMaxPlots = 32`.
- [Skia `main` `aac31b04` `include/gpu/ganesh/GrContextOptions.h`](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/include/gpu/ganesh/GrContextOptions.h#L112-L115)
  — `fGlyphCacheTextureMaximumBytes = 2048 * 1024 * 4`, the 8 MiB default that
  selects the atlas row.
- [Skia `main` `aac31b04` `src/gpu/graphite/text/TextAtlasManager.h`](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/gpu/graphite/text/TextAtlasManager.h#L49-L58)
  — Graphite repeats the same 2048 precision comment and dimension table.
- [Skia `main` `aac31b04` `src/core/SkStrikeCache.h`](https://github.com/google/skia/blob/aac31b0402d507b2354de14ac224ab73cabbeae9/src/core/SkStrikeCache.h#L29-L37)
  — 2 MiB / 2048-entry strike cache budgets (not a texture).
- [`memononen/nanovg` master `ce3bf745` `src/nanovg.c`](https://github.com/memononen/nanovg/blob/ce3bf745eb2d2dbc14a50bf2446783f691ac4353/src/nanovg.c#L40-L42)
  — 512 initial, 2048 max, 4 images, doubling the shorter side, `fonsResetAtlas`.
- [`memononen/nanovg` master `ce3bf745` `src/nanovg_gl.h`](https://github.com/memononen/nanovg/blob/ce3bf745eb2d2dbc14a50bf2446783f691ac4353/src/nanovg_gl.h#L854-L861)
  — the `glTexSubImage2D` dirty-rect upload path.
- [`alexheretic/glyph-brush` `9da30ea` `draw-cache/src/lib.rs`](https://github.com/alexheretic/glyph-brush/blob/9da30eacb54534248f62743f8e5a171694e529e1/draw-cache/src/lib.rs#L198-L208)
  — 256x256 default (`DrawCacheBuilder::default()`).
- [`alexheretic/glyph-brush` `fc70546` `src/glyph_brush.rs`](https://github.com/alexheretic/glyph-brush/blob/fc705469477292cd31fe0f99d3f2644c173bd9db/glyph-brush/src/glyph_brush.rs#L490-L500)
  — `TextureTooSmall { suggested: (w*2, h*2) }`, no auto-growth.
- [`grovesNL/glyphon` `7f43114` `src/text_atlas.rs`](https://github.com/grovesNL/glyphon/blob/7f43114dd17bfe15916127d4132ae3b981f3d5b3/src/text_atlas.rs#L30-L35)
  — `INITIAL_SIZE = 256`, x2 growth capped by the device limit.
- [`pop-os/cosmic-text` `c24886c` `src/swash.rs`](https://github.com/pop-os/cosmic-text/blob/c24886c2471e5606587c46090cd25dbbf209186b/src/swash.rs#L131-L152)
  — no atlas: a per-glyph image hash map.
- [`mooman219/fontdue` `b74746f` `src/font.rs`](https://github.com/mooman219/fontdue/blob/b74746f111056dba7c30162b18403dd6b6f7edbd/src/font.rs#L509-L529)
  — no packed atlas: one bitmap per rasterization.
- [`redox-os/rusttype` `99ee967` `src/gpu_cache.rs`](https://github.com/redox-os/rusttype/blob/99ee9676e3143337806a4e4aab6ece1fad04ae82/src/gpu_cache.rs#L244-L253)
  — `dimensions: (256, 256)` default, immutable dimensions.
- [`needle-mirror/com.unity.textmeshpro` 3.2.0-pre.15 `TMPro_FontAssetCreatorWindow.cs`](https://github.com/needle-mirror/com.unity.textmeshpro/blob/3.2.0-pre.15/Scripts/Editor/TMPro_FontAssetCreatorWindow.cs)
  — Creator window default 512x512 and the 8..8192 resolution list.
- [`needle-mirror/com.unity.textmeshpro` 3.2.0-pre.15 `TMP_FontAsset_CreationMenu.cs`](https://github.com/needle-mirror/com.unity.textmeshpro/blob/3.2.0-pre.15/Scripts/Editor/TMP_FontAsset_CreationMenu.cs#L169-L173)
  — the 1024x1024 creation default and its comment.
- [`needle-mirror/com.unity.textmeshpro` 3.2.0-pre.15 `TMP_FontAsset.cs`](https://github.com/needle-mirror/com.unity.textmeshpro/blob/3.2.0-pre.15/Scripts/Runtime/TMP_FontAsset.cs#L1957-L1985)
  — `m_IsMultiAtlasTexturesEnabled`, the `while` overflow loop, `SetupNewAtlasTexture`.
- [Unity TMP 3.2 manual, Font Asset Creator](https://docs.unity3d.com/Packages/com.unity.textmeshpro@3.2/manual/FontAssetsCreator.html)
  — the only first-party prose on choosing a resolution ("512 x 512 is fine for
  most fonts, as long as you are only including ASCII characters").
- [`Chlumsky/msdf-atlas-gen` `6148900` `main.cpp`](https://github.com/Chlumsky/msdf-atlas-gen/blob/6148900d59423059bafde2f51a0cb303184404bd/msdf-atlas-gen/main.cpp#L96-L101)
  — `-dimensions`, the dimension constraints, the single-atlas hard failure.
- [`Chlumsky/msdf-atlas-gen` `6148900` `README.md`](https://github.com/Chlumsky/msdf-atlas-gen/blob/6148900d59423059bafde2f51a0cb303184404bd/README.md#L93-L103)
  — `-pots` / `-potr` / `-square` / `-square4` documented, `-square4` the default.
- [`godotengine/godot` 4.3-stable `text_server_adv.cpp`](https://github.com/godotengine/godot/blob/4.3-stable/modules/text_server_adv/text_server_adv.cpp#L833-L873)
  — the computed page side (`next_power_of_2`), the 1024/2048 clamps, the
  add-a-page overflow.
- [`godotengine/godot` 4.3-stable `text_server_adv.h`](https://github.com/godotengine/godot/blob/4.3-stable/modules/text_server_adv/text_server_adv.h#L163-L224)
  — `ShelfPackTexture` / `Shelf` (shelf packing, least-waste placement) and the
  1024 field defaults that the packer never uses. Godot `master` (`30caae98`,
  2026-09-24) writes the same page-side decision with a different expression,
  `int texsize = MAX(p_data->size.x * 0.125, 256);`; the two branches express the
  font size in different units and this document does not resolve which, so the
  4.3-stable form above is the one used here (**unit interpretation unverified**).

Repository (evidence, not external):

- `kge-text-ttf/src/commonMain/kotlin/dev/staticsanches/kge/text/ttf/GlyphAtlas.kt`
  — `CHART_SIZE = 512` (:132), shelf placement (:50-69), fail-fast oversized box
  (:51-53), white+alpha encoding (:134-153).
- `kge-text-ttf/src/commonMain/kotlin/dev/staticsanches/kge/text/ttf/Font.kt`
  — one atlas per `sizePx`, created lazily (:41-57); only `sizePx > 0` is required.
- `kge-core/.../renderer/gl/service/GLService.kt` — `texSubImage2D` in the seam
  (:69-80) and its `Proxy` forwarding (:298-308); `texImage2D` (:56-67).
- `kge-core/.../renderer/gl/resource/Texture.kt` — `update` is a full
  `glTexImage2D` (:33-46); `create` allocates RGBA8 with `texImage2D(..., null)`
  (:76-109).
- `kge-core/.../renderer/decal/Decal.kt` — `update()` → `Renderer.updateTexture`
  (:52), creation path (:94-105).
- `kge-core/.../renderer/Renderer.kt` / `internal/DefaultRenderer.kt` —
  `updateTexture` (:51-54 / :47-50).
- `kge-core/.../text/DrawStringService.kt` — the 128x48 NPOT sheet (:276-277),
  `NEAREST` + `CLAMP_TO_EDGE` upload (:287).
- `kge-core/src/jvmMain/.../engine/DriverServiceJvm.kt:38-41` (GL 3.3 core) and
  `kge-core/src/webMain/.../engine/DriverServiceWeb.kt:100` (WebGL2) — the two
  backing specs.
- `docs/decisions/phase-1/36-kge-text-ttf-round-d.md` — the pinned Roboto 3.015
  box/bearing/coverage table used in §4.2.
- `docs/plans/2026-09-20-renderer-lever-measurements.md` §3.3 — the measured
  `bindTexture` dedupe cost cited in §4.4.
- `git show a867d1d:kge-core/src/main/kotlin/dev/staticsanches/kge/configuration/Configuration.kt`
  — the provenance of 512 and its power-of-two `check`.
