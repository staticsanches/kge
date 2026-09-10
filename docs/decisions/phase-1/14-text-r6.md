## 2026-09-10 — Ordering revision: elaborate text to the end (R6); font-library research

### Decision (owner)

- **The old `C7` (simple text) is dropped.** Text is a single, final concept
  (`R6`) with four layers: **shaping** (Unicode → glyph ids + advances/offsets,
  kerning/ligatures), **rasterization** (outline → coverage bitmap), **atlas**
  (packing), and **blit** (CPU to a `MutablePixmap`/layer + the decal/GPU
  variant). The `main` hardcoded bitmap font is **not ported** and is not the
  basis.
- **Reason for the move:** text is not the focus of the `main` restructure and
  has no real consumer until the engine presents a layer; keeping it last avoids
  a provisional text API a later concept must break (roadmap "no throwaway
  commits").
- **Effective order:** `R2` viewport/clipping → `C8` state → `C9`
  renderer/GL/decals (R5 → R4 → R3) → `C10` engine (E1 loop/window + E2 addons +
  E4 KeyCode/InputAction) → `R6` elaborate text.
- **Library choice is NOT made now.** It is decided at the R6 touch-point,
  preceded by a short feasibility spike on the UNVERIFIED items below. Do not
  re-research the landscape: the findings are recorded here.
- **Scope note:** bidi / script itemization are out of the initial R6. HarfBuzz
  shapes an already-ordered run of text; it does **not** implement the Unicode
  Bidirectional Algorithm, so direction resolution and script itemization must be
  supplied separately if ever needed.

### Verified facts (add-time checks, 2026-09-10; primary sources)

Context: the three targets are JVM, `js`, `wasmJs`; the parity floor wants the
same rasterizer to produce the same pixels on all three so pixel tests can pin
results. The old attempt (`origin/fontDevelopment`) used LWJGL
`org.lwjgl.util.freetype` + `org.lwjgl.util.harfbuzz`, JVM-only, coupled to a GPU
`Decal`/`Renderer` that does not exist yet — that is why it did not work; it is
reference only.

**Native/JVM**
- `org.lwjgl:lwjgl-harfbuzz` and `org.lwjgl:lwjgl-freetype` **3.4.3**
  (2026-08-23; Maven directory listing authoritative, `search.sonatype` lags at
  3.3.6). Bindings POM license BSD-3-Clause; bundled natives are HarfBuzz
  ("Old MIT") and FreeType (FTL OR GPLv2). Native classifiers cover
  freebsd/linux(+arm/ppc/riscv)/macos(+arm64)/windows(+arm64/x86). **JVM only** —
  no js/wasmJs form; cannot satisfy the parity floor by itself.

**HarfBuzz on web**
- `harfbuzzjs` (HarfBuzz org, npm) **1.6.1** (2026-08-31), MIT. Ships ESM
  `harfbuzz.js` + `harfbuzz.wasm` (~420 KB) + `harfbuzz-subset.wasm`; runs in
  browser and Node. Full OpenType GSUB/GPOS shaping incl. complex scripts and
  variable fonts; `HB_TINY` build has **no hinting, no AAT, no legacy `kern`
  table**. The main `.wasm` is an **Emscripten module, not standalone**
  (imports `wasi_snapshot_preview1.proc_exit`, `env._abort_js`,
  `emscripten_resize_heap`, etc.; the JS glue installs callbacks via
  `Module.addFunction`). No rasterization (outlines only via `glyphToPath`).
  Reaches Kotlin/JS via npm; Kotlin/wasmJs via `@JsModule` — **UNVERIFIED** as a
  concrete package import (Beta; `.wasm` asset resolution in
  `browser()`/`nodejs()`).

**FreeType on web**
- No official FreeType-published wasm. Emscripten has a **port** only
  (`tools/ports/freetype.py`, `VER-2-14-3`) used when building your own project.
- `freetype-wasm` (Ciantic) **0.0.4** (2022-06-24, npm) — **dead**; built
  without `ALLOW_MEMORY_GROWTH`, fixed ~16 MB heap, OOM on large fonts.
- `@zkl2333/freetype-wasm` **2.14.3** (2026-07-23, npm; package MIT, bundles
  FreeType FTL/GPLv2) — current, `ALLOW_MEMORY_GROWTH=1`, full C API, Node +
  browser + workers. Emscripten/JS-interop dependency.

**Pure-JS font engines (path-only, no rasterizer)**
- `opentype.js` **2.0.0** (2026-05-06, MIT) — parse + outlines + `kern`/GPOS
  kerning + `liga`/`rlig` only; not a full shaper.
- `fontkit` **2.0.4** (2024-08-09, MIT) — real GSUB/GPOS + AAT shaping,
  variations, subsetting; no rasterizer; last release 2024, slow but alive.

**Existing KMP libraries**
- `org.jetbrains.skiko:skiko` (latest `0.152.0-alpha02`, stable `0.150.1`;
  Skiko Apache-2.0, Skia BSD-3) — **JVM + Kotlin/JS + `wasmJs`** (browser).
  Bundles **Skia + HarfBuzz + FreeType**; on web it is its own Skia wasm build
  (Emscripten), not CanvasKit JS. `Paragraph`/`TextLine` (glyph ids, positions,
  `TextBlob`, metrics) are in `commonMain`, so the same API exists on every
  target. The strongest same-rasterizer parity candidate; cost = multi-MB wasm
  and a large Skia API. Whether the shaping entry point needed is public on the
  wasm build is **UNVERIFIED**.
- `org.korge:korlibs-image` **6.1.0** (MIT; jvm/js/wasmJs) — pure-Kotlin
  TTF/OpenType parser with glyph outlines and a partial GSUB (ligatures); **no
  kerning** (`getKerning` is `@TODO`), no GPOS/mark, no bidi. Cheapest KMP
  option but insufficient for elaborate text.
- No dedicated KMP HarfBuzz binding exists on Maven or npm.
- `org.jetbrains.compose` **1.12.0** text — JVM + `wasmJs` but **not `js`**
  (Compose Web is wasm-only), and it is a UI framework; use Skiko directly
  instead.

**One-wasm-everywhere (Godot-style)**
- `com.dylibso.chicory:runtime` **1.7.5** (Apache-2.0) — pure-Java wasm runtime,
  no JNI, WASI p1 + host functions. JVM wasm-GC still roadmap (not needed by
  these modules). `wasmtime-java` **0.19.0** is stale (2023).
- `org.korge:korlibs-wasm` **6.1.0** — pure-Kotlin wasm interpreter targeting
  jvm/js/wasm/native.
- No maintained combined HarfBuzz+FreeType C-ABI wasm artifact exists; you would
  build a `-sSTANDALONE_WASM` shim yourself. Hosting Emscripten's dynamic
  `addFunction` table under Chicory/korlibs-wasm is **UNVERIFIED**; JVM
  interpreter raster performance is **UNVERIFIED**.

**Determinism / parity**
- HarfBuzz positions are `int32_t` and its own test suite pins exact integer
  advances → shaping parity is well-founded **if the same version/feature set is
  compiled** (LWJGL natives vs `HB_TINY` web builds differ).
- FreeType byte-identical native-vs-wasm output has **no primary-source
  guarantee (UNVERIFIED)**; output depends on version, `ftoption.h`/config,
  hints, render mode. Parity must be engineered (one core: Skia or a single
  shim) or proven per font/size with golden tests, with pinned version + hinting
  + AA/gamma/subpixel settings.

**Licensing:** no blocker. FreeType FTL requires only attribution (not
copyleft); GPLv2 is the dual *alternative*. HarfBuzz "Old MIT"; Skiko
Apache-2.0; Skia BSD-3; Chicory Apache-2.0; JS engines MIT.

### Candidate stacks (ranked; for the R6 touch-point)

1. **Skiko/Skia** — JVM native + Skia wasm on js/wasmJs; same Skia/HarfBuzz/
   FreeType family → best available parity. Medium effort, heavy wasm, large API.
2. **Web = `harfbuzzjs` + `@zkl2333/freetype-wasm`; JVM = LWJGL** — fastest to
   stand up, all packages current; parity only by golden tests (different
   versions/configs), per-glyph JS boundary on wasmJs.
3. **One custom wasm shim + Chicory (JVM) + `WebAssembly`/korlibs-wasm (web)** —
   best one-binary parity story; highest effort; JVM interpreter perf unproven.
4. **Browser canvas + AWT (+ node canvas)** — lowest effort, three rasterizers,
   fails the parity floor.
5. **korlibs-image `TtfFont`** — cheap and already wasmJs, but no kerning/GPOS/
   bidi; reference only, not a foundation.

### Spikes to run before choosing (at the R6 touch-point)

1. Skiko on **`js`** — shape/layout a fixed string (e.g. Roboto 16px) and compare
   glyph ids/positions and alpha bitmaps vs JVM.
2. Skiko wasm payload size + startup cost; complex-script coverage (confirm
   SkShaper/HarfBuzz is compiled into the release wasm).
3. If going non-Skia: `harfbuzzjs`/FreeType-wasm importable on Kotlin/wasmJs
   (`@JsModule`, asset resolution) and whether Chicory/korlibs-wasm can host the
   Emscripten glue.
4. FreeType native-vs-wasm byte-identity on one font/size (golden test).

Raw investigation notes (superseded by the above) lived in
`.tmp/font-research-harfbuzz.md` and `.tmp/font-research-alternatives.md`
(gitignored scratch, not committed).
