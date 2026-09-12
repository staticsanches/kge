# KGE Restructure — Phase 1 Decisions & Verification Log

Append-only log of verified facts and per-concept decisions for the `main`
restructure. Opened at Task 2 (2026-08-30) to record plugin-toolchain findings.

The log was **split by concept era** (2026-09-10) so a session reads only the
part it needs instead of the whole history. This file is the index; the entries
live in `phase-1/`. Entry numbers are global across the log (early entries carry
`### N.` headers; later concept entries are cited as `#N` in prose and
cross-references).

## How to read

- Read this index, then **only the chunk(s) relevant to the concept at hand** —
  a concept's touch-point, decisions and close facts are one chunk.
- Cross-cutting build/toolchain facts are in `01` (setup), `04` (CI), `09`
  (toolchain refresh / build warnings) and `10` (gate tooling).
- Do not read every chunk. `grep` the `phase-1/` directory for a fact by keyword
  or entry number.

## How to append

- New entries go at the **end of the current chunk**; when a new concept starts,
  create the next numbered chunk and add a row here.
- Keep entry numbers continuous; keep the header format
  `## YYYY-MM-DD — <concept/decision>`.
- Never rewrite history in a chunk — supersession is a new entry (as in `#28`).

## Chunks

| Chunk | Entries | Contents | Dates |
|---|---|---|---|
| [`01-project-setup.md`](phase-1/01-project-setup.md) | 1–11 | `kge-core` skeleton + scaffold verification (KSP, ktlint, hierarchy, jvmTest false green) | 2026-08-30 |
| [`02-buffer-attempt-roadmap.md`](phase-1/02-buffer-attempt-roadmap.md) | 12–23 | LWJGL/kotlinx-browser/endianness facts; macro roadmap landed; C4 material; buffer attempt disposition | 2026-08-30/31 |
| [`03-pixel-c4.md`](phase-1/03-pixel-c4.md) | 24 | C4 (Pixel) touch-point + close: value class, `Colors` (CSS Color 4) | 2026-09-01 |
| [`04-ci-windows.md`](phase-1/04-ci-windows.md) | 25, 36 | Windows root yarn tasks: `kotlinWasmStoreYarnLock` lock flake + `wasmJsBrowserTest` missing `kotlin-web-helpers`, diagnosed + fixed | 2026-09-01/12 |
| [`05-extension-c1.md`](phase-1/05-extension-c1.md) | 26–27 | C1 extension mechanism (`KGEContext`, identity-semantics amendment) | 2026-09-01 |
| [`06-overridable-t2.md`](phase-1/06-overridable-t2.md) | 28 | **T2 redesign**: `KGEOverridable` supersedes `KGEContext` | 2026-09-02 |
| [`07-pixel-format-t3.md`](phase-1/07-pixel-format-t3.md) | 29 | T3 `PixelFormatService` (first real T2 consumer) | 2026-09-02 |
| [`08-resources-c2.md`](phase-1/08-resources-c2.md) | 30 | C2 (T1): resource contract, `LeakReporterService`, leak detection, web observation spike | 2026-09-03 |
| [`09-toolchain-memory-c3.md`](phase-1/09-toolchain-memory-c3.md) | 31 | Gradle 9.7.1 + ktlint-gradle 14.2.0; C3 (S1) native memory `ByteBuffer`; KT-61573 cleanup | 2026-09-03/04 |
| [`10-gate-surface-c5.md`](phase-1/10-gate-surface-c5.md) | 32–33 | ktlint wired into `build`; C5 (S3/S4) `Pixmap`/`MutablePixmap`/`Sprite` | 2026-09-05 |
| [`11-png-s5.md`](phase-1/11-png-s5.md) | 34 | S5 PNG codec: touch-point + close (`PngService`, `PngSource`) | 2026-09-07/08 |
| [`12-raster-c6.md`](phase-1/12-raster-c6.md) | 35 | C6 (R1) raster: touch-point, close, post-close fixes | 2026-09-08/09 |
| [`13-vector-point.md`](phase-1/13-vector-point.md) | — | `Int2D`/`Float2D` + typed raster overloads | 2026-09-09 |
| [`14-text-r6.md`](phase-1/14-text-r6.md) | — | Ordering revision (elaborate text last, `C7` dropped) + font-library research | 2026-09-10 |
| [`15-viewport-r2.md`](phase-1/15-viewport-r2.md) | — | R2 viewport/clipping: touch-point + close (pure `Viewport`, `ClipService` seam, `Pixmap : Viewport.Bounded`, drawLine clip-then-walk parity) | 2026-09-10 |
| [`16-window-blit.md`](phase-1/16-window-blit.md) | — | Post-R2 window + partial blit: anonymous `Pixmap.window`/`Pixmap.Mutable.window` (local `0..size` + `origin`), `BlitService`/`blit`/`blitRegion` over a `Pixmap` source, `Flip` moved to `Pixmap`, nested `Pixmap.Mutable`/`Pixmap.RawBacked` | 2026-09-10 |
| [`17-circle-octant-mask.md`](phase-1/17-circle-octant-mask.md) | — | Post-R2 circle octant masks: `CircleOctantMask` on `drawCircle`/`fillCircle`; type in `rasterizer`, clockwise-from-top O1..O8, odd-owns-boundaries, `ALL` keeps C6 parity | 2026-09-11 |
| [`18-line-pattern.md`](phase-1/18-line-pattern.md) | — | R1 line patterns: `LinePattern` (sealed, stateful) on `drawLine` + propagated to `drawRect`/`drawTriangle`; required, after `color`; pattern phase from the first cell of the clipped walk | 2026-09-11 |
| [`19-image-s6.md`](phase-1/19-image-s6.md) | — | S6 image service (supersedes S5): `ImageService` + generic `Decoder<T>`/`Encoder<T>`, suspend `load`/`save`, RGBA-only `Sprite`, PNG/JPEG uniform encode; web backend browser-native (`createImageBitmap`/canvas, node dropped, browser tests in CI), documented per-platform decode divergence | 2026-09-11 |
| [`20-state-c8-touchpoint.md`](phase-1/20-state-c8-touchpoint.md) | — | `C8` state (E3) dissolved into `C10`: consumer-lens rationale + time/`DimensionState`/`WithKGEState` findings carried to the C10 touch-point; `C9` becomes next | 2026-09-11 |
| [`21-gl-testability-spike.md`](phase-1/21-gl-testability-spike.md) | — | GL testability spike (C9 seed): JVM hidden GLFW + GL 3.3 + FBO readback (needs `-XstartOnFirstThread` on macOS), `webTest` shared source set, `kotlin-browser` WebGL2 + SwiftShader Karma launcher; macOS runner ships Chrome — CI exclusions removed, `setup-chrome` + `xvfb` added | 2026-09-12 |
