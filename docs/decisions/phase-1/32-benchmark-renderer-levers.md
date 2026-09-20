## 2026-09-20 — Renderer submission levers: measurement apparatus + verdicts

The renderer/text submission candidates (`#1` merge instances, `#2` upload
strategy, `#3` coalesced submission, `#4` state dedupe, `#6` `glMultiDrawArrays`)
were priced instead of debated. The apparatus and the full tables are in
`docs/plans/2026-09-20-renderer-lever-measurements.md`; this entry records the
verdicts. **No engine behavior or public API changed** — this is measurement code
inside `kge-benchmark` (`BenchmarkWorkload`, `Scene.renderTextScene`,
`MergedDrawStringService`, `BatchGLCalls`, `BenchmarkOptions`, the query-string
entry point with `#raw`/`report`), its tests (`MergedRunGeometryTest`,
`TextSceneTest`) and `tools/benchmark-report-server.py`.

### Method facts (verified, reusable)

- **Headless Chrome drops the real GPU silently.** Verify the renderer through
  `WEBGL_debug_renderer_info` and pass `--ignore-gpu-blocklist`
  (`--use-angle=metal` on the macOS runs). The
  `--disable-gpu --enable-unsafe-swiftshader` fallback rasterizes in software and
  inflated the per-glyph cost 4–10×, which is what produced the retracted
  "web draws cost 4–5× the JVM" claim.
- **`--virtual-time-budget` is unusable for this loop**: it distorts the
  `requestAnimationFrame` pacing the engine runs under. Real time plus the
  `report=<url>` collector is the working shape; uncapped numbers additionally
  need `--disable-frame-rate-limit --disable-gpu-vsync` (the `empty` cell goes
  from 60.01 fps paced to 662–781 fps uncapped).
- **The lever decorator sits below the renderer**, so every measured gain is a
  lower bound of moving the same logic into `DefaultRenderer`.
- Comparison discipline: against `renderer-passthrough` (same wrapper, no lever),
  because the wrapper's own cost drifts ~±5% between runs.

### Verdicts

- **`#2` persistent buffer + per-draw `bufferSubData`: refuted by measurement.**
  992 ms/frame (1 fps) on the JVM and 266 ms/frame (3.76 fps) on the web, against
  6.56/7.82 ms for the same load with per-draw orphan `glBufferData`. The orphan
  at exact size is the correct strategy for draws this small; a reused buffer
  serializes the upload against in-flight work. Do not implement, on either
  platform. (`#2` combined with `#4` is worse, not better: 931 / 1293 ms.)
- **`#4` splits in two.** The blend-mode guard is **not** an optimization: its
  delta sits inside the ~±5% run band on both platforms (JVM −2.6%, web −0.6%),
  and both olc (`Renderer_OGL33::SetDecalMode`, guarded by `nDecalMode`) and
  `main` (`BaseRenderer.decalMode`) already had it — so the core's unconditional
  `glBlendFunc` per instance is an unrecorded **divergence from olc and a
  regression against `main`**, and is corrected in its own round. The remaining
  dedupe — `disable(GL_CULL_FACE)` and `bindTexture`, which **neither reference
  guards**: 6.56 → 5.01 ms JVM (−23.6%) and 7.82 → 6.67 ms web (−14.7%) — belongs
  to the `#3` round, in olc's reset-in-`prepareDrawing` shape rather than as a
  general cache.
- **`#1` merge the text run: real, deferred.** One triangle-list instance per
  string costs 0.38 ms against 6.46 ms per-glyph on the JVM (**17×**) and 2.25 ms
  against 5.44 ms on the web (**2.4×**). The web residual is the `VerticesInfo`
  pull path in wasm, ~17 ns per vertex on the JVM against ~98 ns on the web as
  implied by those totals (six vertices per glyph), not submission. It stays a
  measured candidate because it is a public `DrawStringService` decision, not a
  renderer tweak.
- **`#3` coalescing.** Its non-`#4` content requires a frame-boundary flush hook
  the renderer does not have; not attempted. Candidate for a dedicated round.
- **`#6` `glMultiDrawArrays`: rejected as a core strategy.** Not core in WebGL2
  (it is `WEBGL_multi_draw`, absent in Firefox) and it takes one primitive mode,
  so mixed-structure runs cannot use it.
- **Per-draw submission cost is comparable across the two backends** (JVM
  1.69–2.26 µs, web real GPU 1.32–1.42 µs per glyph draw across the repeated
  cells; the totals imply 1.69 and 1.42 µs), correcting the SwiftShader-era claim.

### Apparatus limitations (recorded, not open findings)

- **The merged geometry's parity with the default service is review-verified, not
  test-verified.** `MergedRunGeometryTest` pins the exact values the default
  `DrawStringService` decal path produces plus the quad's shape invariants, but it
  cannot *run* that path: the GL/sprite doubles it needs live in `kge-core`'s test
  source set, which a downstream module cannot reach. Automating the link would
  mean duplicating a recording `GLService` and its per-platform handle factories
  into `kge-benchmark`; the two-axis Spec review verified the arithmetic
  component-by-component instead, and the merged builder is frozen measurement
  code.
- **The lever decorator guards the blend pair, while the core should guard the
  mode** (as olc and `main` do), so the decorator is strictly stronger; the
  measured blend cell only draws `NORMAL` instances, where the two coincide. The
  lower-bound direction is a separate fact: the decorator sits *below*
  `Renderer`, so a core change that moved the same logic inside would also save
  the facade dispatch. Neither number may be quoted as a core round's gain.
- **`TextPerGlyphList` does not draw the strip's quad.** The default mono decal
  path emits a four-vertex instance; drawn as `GL_TRIANGLES` only its first three
  vertices are used, so that cell rasterizes one triangle per glyph. No recorded
  verdict cites it; the cell is kept and labelled for what it does.
- **`report` is parsed for both entry points and sent by the web one only**, so
  the JVM accepts an option it does not act on.
- **`#2` is emulated conservatively**: the per-draw upload is redirected into one
  buffer with `bufferSubData`, which is at best as good as the renderer could do,
  so its catastrophic result is sound.
