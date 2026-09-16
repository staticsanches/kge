# Elaborate text (R6) — shaping, rasterization, atlas, blit: touch-point

**Date:** 2026-09-16. Touch-point for `R6`, the last concept of the restructure.
It closes the stack/scope decisions left open by the recorded font-library
research (do not re-research) and records the findings of the feasibility spikes
run for this touch-point. Status: **decided 2026-09-16** (owner). The micro-plan
is written just-in-time on top of this.

## Real consumer

A user application that subclasses `Engine` and draws text into a layer's CPU
draw target (`Rasterizer` over a `Pixmap.Mutable`) and/or as a `Decal` on the GPU
path. There is no such app yet; the FPS benchmark draws only primitives, so it is
not the text consumer.

## Established (research + kernel)

- The four layers of `R6`: **shaping** (Unicode → glyph ids + advances/offsets,
  kerning/ligatures), **rasterization** (outline → coverage bitmap), **atlas**
  (packing), **blit** (CPU to a `MutablePixmap`/layer + the decal variant).
- The `main` hardcoded bitmap font is **not ported** and is not the basis.
- Foundation already closed that `R6` consumes: `Sprite`/`Pixmap.Mutable` +
  `BlitService` (CPU blit), `Decal`/`Renderer` (GPU variant), `ImageService`
  (`S6`, encoded font asset loading), `Layer`/addons (`C10c`), the resource
  contract and `MemoryAllocatorService` (`C2`/`C3`).

## Reference behavior (olc / `main`)

- olc ships a built-in **bitmap** font: `DrawString`/`DrawStringProp` (+ rotated
  and `…Decal` variants), `GetTextSize`/`GetTextSizeProp`, tabs/multi-line, and
  `TextEntryEnable`/console. `main` ported this as
  `DrawStringAddon`/`DrawStringService` (a 128x48 sheet).
- **Neither does TTF shaping or rasterization.** olc parity applies only to the
  *shape* of the drawing API (names, defaults: `color = WHITE`, `scale = 1`,
  tab handling, multi-line), measured/positioned by the TTF font here.

## Touch-point decisions (closed, owner)

1. **Stack: HarfBuzz (shaping) + FreeType (rasterization).** A thin per-platform
   seam (`expect`/`actual`, ~4 functions) binds the two; the layout application,
   **atlas**, **blit**, metrics, glyph cache and addons live in `commonMain`.
   Rejected with recorded rationale:
   - **Skiko/Skia:** one common API and exact shaping, but `skiko.wasm` is
     8.64 MB (+1.12 MB JS) on web and its rasterizer is **not** pixel-identical
     across targets (~5.6% coverage delta JVM×js) — no parity advantage to pay
     9.8 MB for.
   - **Own deterministic rasterizer (option A):** would keep everything in
     common, but requires writing and maintaining a glyph rasterizer and
     forfeits ready hinting; FreeType already gives parity, so the work is
     unjustified.
   - **stb_truetype (option C):** free on JVM (`lwjgl-stb`, already a
     dependency) but **no maintained web/wasm package** exists — the web side
     would require a self-maintained Emscripten build.
   - **One wasm everywhere (Chicory/korlibs-wasm):** byte-identical raster but
     highest effort and unproven JVM interpreter performance.
2. **Scripts: Latin/LTR only.** Unicode Bidirectional Algorithm (bidi, UAX #9)
   and script itemization are **out**; HarfBuzz shapes an already-ordered run.
3. **API: expose the layout, addons on top.** A shaped-run/layout result is
   public; the olc-like `drawString*`/`getTextSize*` are convenience addons over
   it. Exact names/shape are micro-plan detail.
4. **Text entry and dev console: out of scope.** `TextEntryEnable`, cursor and
   the olc console are not ported.
5. **Raster parity: AA differences tolerated** — verified moot for the chosen
   stack (see below).
6. **Licensing: permissive, preferring MIT.** HarfBuzz MIT; `@zkl2333/freetype-wasm`
   package MIT (FreeType FTL/GPLv2); LWJGL BSD-3 (already a dependency).

## Spike findings (2026-09-16, throwaway project under `.tmp/`, discarded)

Thrown-away KMP spike (jvm + js + wasmJs), Roboto-Regular, string
`"AV To Wave 123"` at 16 px.

**Shaping — parity confirmed.** HarfBuzz (JVM native via `lwjgl-harfbuzz` 3.4.3;
web via `harfbuzzjs` 1.6.1, HarfBuzz 14.4.0) produced **identical** glyph ids
`[38,59,5,57,84,5,60,70,91,74,5,22,23,24]` and advances
`[9.765625,10.1875,3.65625,8.78125,9.125,3.96875,13.953125,8.59375,7.75,8.484375,3.96875,8.984375,8.984375,8.984375]`
on JVM and web. GPOS kerning is applied by default (AV: 9.765625 vs 10.4375 solo);
`HB_TINY`'s disabled features (legacy `kern`, hinting, AAT) do not affect this.
Note: Skiko's SkShaper **agrees with itself** across targets but **differs from
HarfBuzz** (~0.45 px on kerned pairs) — the divergence is Skiko's, not HarfBuzz's.

**Rasterization — parity confirmed.** FreeType native (`lwjgl-freetype`, FreeType
2.14.3) and wasm (`@zkl2333/freetype-wasm` 2.14.3) gave **identical** coverage
bitmaps (same version; `FT_LOAD_DEFAULT` + `FT_RENDER_MODE_NORMAL`, i.e. hinting
on):

| char | size | ink | Σ coverage |
|---|---|---|---|
| A | 11x12 | 69 | 10241 |
| V | 10x12 | 60 | 8813 |
| o | 9x9 | 55 | 7714 |
| 1 | 5x12 | 32 | 5416 |

The same values were reproduced through Kotlin/JS (browser). So the tolerance
decision (5) is moot: the chosen stack is byte-identical across targets.

**Payload.** `harfbuzzjs`: `harfbuzz.wasm` 426,620 B + ~0.11 MB JS.
`@zkl2333/freetype-wasm`: `freetype.wasm` 830,398 B + ~0.06 MB JS. Total
≈ 1.4 MB wasm + ~0.2 MB glue on web (vs ~9.8 MB for Skiko).

**Interop friction (recorded for the micro-plan).**
- *js:* `harfbuzzjs` pulls the Emscripten glue that references Node's `module`
  (and `fs`/`path`); the webpack bundle needs `resolve.fallback` entries. Both
  libs' wasm assets load via `import.meta.url`; a `webpack.config.d/*.js`
  fallback was required for HarfBuzz. `freetype-wasm` needed no extra asset glue.
- *wasmJs:* no `dynamic` — use `JsAny`; a top-level `external fun` maps to the
  module's **default** export, so named exports require an `external object` with
  members; a default export import needs `@JsName("default")`. Both libs
  imported and initialized on wasmJs (`harfbuzzjs` version 14.4.0; FreeType
  init). Binary marshalling (`ByteArray` ↔ `Uint8Array`) and option objects
  (`@JsPlainObject`) are micro-plan work. wasmJs shaping/raster were not executed
  (the wasm binaries are the same as js, which matched JVM exactly).
- *JVM:* `lwjgl-harfbuzz`/`lwjgl-freetype` bindings work directly; `FT_Face` is
  created with `FT_Face.create(address)`.

## Derived model (micro-plan boundary)

- **Seam** (`expect`/`actual`, ~4 functions): open/close face; `shape(face, text,
  sizePx)` → glyph ids + advances/offsets; `rasterizeGlyph(face, gid, sizePx)` →
  coverage bitmap + bearing + advance. Backends: JVM (LWJGL), web (`webMain`
  shared over the same npm packages, `expect`/`actual` only for the interop
  differences).
- **`Font` resource** owning the native face and the glyph atlas (`Sprite`),
  registered in the resource scope; created from decoded bytes.
- **Common engine logic:** advance/offset application, atlas packing, glyph
  cache, CPU blit into a `Pixmap.Mutable`, decal variant, metrics, addons.
- **Naming/parameter defaults** and the exact public layout type are micro-plan
  detail (olc-like `drawString*`/`getTextSize*` defaults preserved where they
  map; `scale`/tabs/multi-line semantics decided there).

## Out of scope / deferred

- bidi (UAX #9), script itemization, complex scripts, font fallback.
- `TextEntryEnable`/cursor/dev console.
- SDF/MSDF atlases; 3D; glyph hinting policy changes.
- Web wasm payload optimization (subsetting, brotli) — revisit if size matters.

## Revision (2026-09-16, owner) — split the text work and isolate the deps

The stack decision stands (HarfBuzz + FreeType), but its placement changes: the
dependencies are heavy (web ≈ 1.4 MB wasm + glue; JVM LWJGL HB/FT jars and
natives), so they must not be forced on every `kge-core` consumer.

1. **The bitmap text returns to `kge-core`** (concept `C7`, revived). This
   **reverses the 2026-09-10 decision** that dropped the simple text concept and
   said the `main` bitmap font was not ported. Rationale: the core keeps a
   faithful olc text capability with **zero new dependencies**, and the heavy TTF
   path becomes opt-in.
2. **The elaborate TTF text lives in a new module `kge-text-ttf`** (jvm/js/
   wasmJs), depending on `kge-core`. This is `R6`. The module mirrors the
   `kge-benchmark` layout; `./gradlew build`/CI pick it up.
3. **The module starts with its own API.** After the text feature is delivered,
   we will analyze whether a **compatible API across both** (bitmap and TTF) can
   be defined, to exercise the plug (`KGEOverridable`) — recorded as a follow-up,
   not a commitment now.
4. **Five thin rounds** (each with gate + two-axis review + log entry):
   - **A** — bitmap text in `kge-core` (`C7`): port the olc sheet and
     `drawString`/`getTextSize` (mono + prop) with its addons.
   - **B** — `kge-text-ttf` scaffold: new module, targets, deps, smoke test.
   - **C** — face + HarfBuzz shaping + public layout.
   - **D** — FreeType rasterization + glyph cache/atlas.
   - **E** — CPU blit + addons, and the decal/GPU variant.

The rest of this document still describes `R6` (the elaborate text, now
`kge-text-ttf`); the seam/API details it leaves to the micro-plan apply to that
module.

