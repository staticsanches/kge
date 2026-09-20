# Renderer submission levers — measurement apparatus and findings

**Date:** 2026-09-20. Records the `kge-benchmark` apparatus built to measure the
renderer/text submission candidates, the method used to get trustworthy numbers
on both platforms, the measurements themselves, and the verdict per lever. The
verdicts are recorded as decisions in the log (`#32`); this doc is the evidence
they rest on. Nothing here changes engine behavior.

The apparatus exists because the candidates were being debated as theory: a
decal-heavy frame submits one `glDrawArrays` and one vertex upload per instance,
and the question was which of the possible fixes pays, where, and by how much.

## 1. The apparatus

All of it is measurement-only, inside `kge-benchmark` (an application module, not
part of the library), and none of it changes a core contract.

| piece | what it is |
|---|---|
| `BenchmarkWorkload` | the measured cell: `empty`, `render`, the per-glyph text loads (`text-perglyph-strip`/`-list`), `text-merged-list`, and the five renderer-lever cells (`renderer-passthrough`, `-blend`, `-dedupe`, `-upload`, `-batched`) |
| `Scene.renderTextScene` | the text load: a deterministic block of `SCENE_TEXT_COLUMNS` 44-glyph lines per 12 px row, at 640×360 = 29 rows = 3828 glyphs/frame, tinted from a cycling palette |
| `MergedDrawStringService` | the `#1` candidate, as a benchmark-local `DrawStringService` override: one triangle-list `DecalInstance` per string built into flat vertex arrays with the default service's quantised geometry (six vertices per glyph), instead of one instance per glyph. The font decal is learned once from a one-glyph probe, because the font holder is `internal` |
| `BatchGLCalls` | a `GLService` decorator applying the renderer-side levers **at the GL boundary**: `dedupeBlend`, `dedupeDisable` (`CULL_FACE`), `dedupeTexture`, and `appendUploads` (one persistent 4 MB VBO, orphaned at `viewport`, per-draw `bufferSubData`) |
| `BenchmarkOptions` | the shared `key=value` selection (`sizes`, `modes`, `workloads`, `highDpi`, `warmup`, `measure`, `report`), parsed from `--key=value` on the JVM and from the query string on the web, so one cell can be re-measured in seconds instead of sweeping everything |
| web `#raw` + report | the wasmJs entry point also writes the plain-text table into `#raw` and, with `report=<url>`, GET-reports every cell to a collector — the only way to read a headless run without a DOM dump |
| `tools/benchmark-report-server.py` | that collector |
| `MergedRunGeometryTest`, `TextSceneTest` | the apparatus' own contract: the merged run reproduces the default service's quantised geometry (snapshot plus shape invariants), and the scene stays deterministic |

The decorator deliberately sits **below** the renderer, so the facade dispatch is
still paid: the measured gain of a lever is a **lower bound** of moving the same
logic inside `DefaultRenderer`. `appendUploads` proves the point in the other
direction — as a lower bound it still measured catastrophic, which is a sound
"do not implement".

## 2. Method (and its traps)

- **JVM:** `tools/gradle :kge-benchmark:benchmarkJvm --args="--sizes=640x360 …"`.
- **web:** `:kge-benchmark:wasmJsBrowserDistribution`, served locally, driven by
  headless Chrome against the query string, read through the collector.
- **Real time only.** `--virtual-time-budget` was tried and rejected: it distorts
  the `requestAnimationFrame`-paced loop the engine actually runs under.
- **Real GPU only.** Headless Chrome silently falls back to SwiftShader; verify
  with `WEBGL_debug_renderer_info` (`ANGLE (Apple, ANGLE Metal Renderer: Apple M1)`
  in these runs) and use `--ignore-gpu-blocklist --use-angle=metal`. The
  `--disable-gpu --enable-unsafe-swiftshader` fallback rasterizes in software.
- **Vsync off.** `--disable-frame-rate-limit --disable-gpu-vsync`, otherwise the
  60 Hz pacing hides sub-frame differences (the `empty` cell went from 60.01 fps
  paced to 662–781 fps uncapped).
- **Compare like with like.** The lever cells are compared against
  `renderer-passthrough` (the same decorator, no lever), not against the raw
  baseline, because the wrapper's own cost drifts ~±5% between runs. Differences
  inside that band are reported as noise, not as wins.
- **Derived text cost** is the difference between the text cell and the
  `empty`/`render` cell of the same sweep, so it excludes the clear/upload/present
  work all cells share.

## 3. Measurements

640×360, 3828 glyphs/frame, uncapped, real GPU.

### 3.1 Per-draw cost (per-glyph text, one `glDrawArrays` and one upload per glyph)

| | JVM real GL | web real GPU |
|---|---|---|
| per glyph draw, implied by the totals below | 1.69 µs | 1.42 µs |
| per glyph draw, spread over the repeated cells | 1.69–2.26 µs | 1.32–1.42 µs |
| total text cost | 6.46 ms | 5.44 ms |

Submitting a draw is therefore **roughly the same price on both platforms** (the
6.46/5.44 ms totals over 3828 glyphs). It is not the 4–5× web penalty an earlier,
SwiftShader-based run suggested (see §5).

### 3.2 Merging the text run (`#1`, one triangle-list instance per string)

| | per-glyph (ms) | merged (ms) | gain |
|---|---|---|---|
| JVM real GL | 6.46 | 0.38 | **17×** |
| web real GPU | 5.44 | 2.25 | **2.4×** |

Per merged glyph, from the merged totals: ~0.10 µs on the JVM and ~0.59 µs on the
web — that is ~17 ns against ~98 ns per vertex, since the run writes six vertices
per glyph (3828 glyphs = 22968 vertices). The web residual is the wasm side of
filling the vertex arrays, roughly six times the JVM's per-vertex cost, so the
gain is real on both platforms but capped on the web by that path, not by
submission.

### 3.3 The `#4` decomposition (state dedupe)

| cell | JVM real GL | web real GPU |
|---|---|---|
| baseline | 6.83 ms | 8.07 ms |
| pass-through (control) | 6.56 ms | 7.82 ms |
| **blend-mode guard only** | 6.39 ms → −2.6% (inside the band) | 7.77 ms → −0.6% (inside the band) |
| guard + `disable(CULL_FACE)` + `bindTexture` | 5.01 ms → **−23.6%** | 6.67 ms → **−14.7%** |

The blend guard — the one part **both references already had** — is not
measurable on either platform: both deltas sit inside the ±5% run-to-run band
used throughout this document. Essentially all of the dedupe win comes from the
`CULL_FACE` and `bindTexture` parts, which **neither olc nor `main` guards**.

### 3.4 The `#2` candidate (persistent buffer + per-draw `bufferSubData`)

| | JVM real GL | web real GPU |
|---|---|---|
| persistent buffer only | **992 ms/frame (1 fps)** | **266 ms/frame (3.76 fps)** |
| with the `#4` dedupes | 931 ms | 1293 ms |

Refuted, decisively, on both platforms. Replacing a per-draw orphan
`glBufferData` (exact size, `STREAM_DRAW`) with a `bufferSubData` into a reused
buffer serializes the upload against in-flight GPU work: for the tiny draws this
engine submits, the orphan is the correct strategy. The result reproduces on the
WebGL2 backend, so it is not a driver quirk.

## 4. Verdict per lever

| lever | verdict |
|---|---|
| `#1` one instance per string | Real and large (17× JVM / 2.4× web on text cost) but it is a **public API decision** — who owns merging, and what `DrawStringService` promises — not a renderer tweak. Kept as a measured candidate; not implemented in core. |
| `#2` persistent buffer + `bufferSubData` | **Refuted by measurement.** Do not implement. |
| `#3` coalesced submission / batching | Needs a frame-boundary flush hook that does not exist yet (the renderer has no end-of-frame entry point). Its remaining content, after `#4`, is exactly the `CULL_FACE`/`bindTexture` dedupe. Candidate for a dedicated round. |
| `#4` state dedupe | Split: the **blend-mode guard is a parity correction** (both references have it; the core lost it) and gets its own round. The `CULL_FACE`/`bindTexture` dedupe is the real win and belongs with `#3`, in olc's reset-in-`prepareDrawing` shape rather than as a general cache. |
| `#5` instancing | Not measured; the per-draw fix it would address is superseded by `#1`/`#3` and it has no core seam. Out of scope. |
| `#6` `glMultiDrawArrays` | Not core in WebGL2 (it is the `WEBGL_multi_draw` extension, absent in Firefox) and it takes a single primitive mode, so it cannot serve mixed-structure runs. **Rejected as a core strategy.** |

## 5. Corrections to earlier claims

Recorded because they were stated during the investigation and would otherwise
persist as facts:

- **"A web draw costs 4–5× a JVM draw."** Wrong. That run used the SwiftShader
  fallback (per-glyph cost inflated 4–10×: web text cost 34.5 ms at the time).
  On a real GPU the per-draw costs are close (§3.1).
- **"The renderer is missing an optimization for the blend mode."** Wrong. It is
  missing a **guard** that olc and `main` both had, and the guard's own delta sits
  inside the run band on both platforms (§3.3), so the correction is parity, not
  speed.
- **"Dedupe the vertex upload by keeping one buffer."** Wrong in the strongest
  sense: against its pass-through control the upload lever is 151× worse on the
  JVM and 34× on the web, and combining it with the `#4` dedupes reaches 186–194×
  against those dedupe-only controls (§3.4).

## 6. Non-goals

- The apparatus is not production code: `MergedDrawStringService` is a candidate
  implementation for measurement, and the lever cells exist to price levers, not
  to ship them.
- This round changes no engine behavior and no public API; it adds measurement
  code, its tests, the collector tool, and this record.
