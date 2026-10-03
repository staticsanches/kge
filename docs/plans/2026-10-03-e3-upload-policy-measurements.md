# E3 coverage-texture upload policy: region vs. whole-chart re-upload

**Date:** 2026-10-03. The measurement decision 6 of the round E3 touch-point
ordered: price the carrier's region update against a whole-chart re-upload, on
both shipped backends, at the renderer-lever round's rigor. This document is the
apparatus and the numbers; the verdicts are in decisions entry `40`.

## What was measured

The E3 carrier (`GlyphAtlasGpu`) keeps one 512x512 chart texture per atlas chart.
A newly placed glyph is uploaded as a **region update**: `texSubImage2D` of the
glyph's box. The alternative priced here is a **whole-chart re-upload**:
`texImage2D` of the entire 512x512 chart.

A benchmark-only `GLService` decorator (`UploadPolicyGLCalls`, in
`kge-benchmark`) shadows each chart the carrier seeds, records the region updates
it sees, and replays them under one of two policies per frame:

| policy | per measured frame |
|---|---|
| `REGION` | one `texSubImage2D` per recorded box, from the box's pre-packed buffer |
| `FULL` | **one** whole-chart `texImage2D` per touched chart, from the shadow |

`FULL` prices one upload per **chart**, not one per glyph: a re-upload policy
re-sends the chart a frame touched, and charging it per box would overstate it by
the number of boxes. 21 boxes over one chart is the load both cells carry.

The `REGION` cell is also the control: both cells run the **same** decorator with
the same recording and the same shadow maintenance, so the wrapper's own cost is
present in both and cancels in the delta, which then isolates the policy. That is
the lever round's `renderer-passthrough` discipline, and it is why neither cell is
compared against a raw undecorated baseline.

## The gap that shaped the apparatus

**The carrier uploads each placement exactly once.** A frame that draws a text
already drawn issues no `texSubImage2D` at all, so a cell that simply drew text
through the carrier would price zero against zero and spend its whole warmup and
measurement window in steady state. Measured before anything was built, with the
recording service driving a headless engine (now pinned permanently by
`TtfCarrierUploadTest`):

| frame | `texSubImage2D` |
|---|---|
| build (first) | 21 |
| every steady-state frame | 0 |

The replay exists because of that: the decorator replays the **real boxes the
carrier actually produced**, so the measured window carries realistic upload
traffic. The consequence is recorded rather than hidden — in the engine as
shipped, the region policy is a **build-time** cost, not a per-frame one.

## Method, and the correction that decided the result

Protocol: 640x360, uncapped, `highDpi=false`, warmup 2 s, measure 5 s, three runs
per cell, real GPU.

- **JVM:** `tools/gradle :kge-benchmark:benchmarkJvm --args="--sizes=640x360
  --modes=uncapped --highDpi=false
  --workloads=text-ttf-region,text-ttf-full --warmup=2 --measure=5"`
- **Web:** `wasmJsBrowserDistribution` served locally, three headless Chrome
  loads with `--ignore-gpu-blocklist --use-angle=metal
  --disable-frame-rate-limit --disable-gpu-vsync` against the query string, read
  through `tools/benchmark-report-server.py`. Chrome needs
  `--no-sandbox --disable-gpu-sandbox` on this host (its own sandbox cannot
  initialize); the flags do not change the renderer.
- **Real GPU verified, not assumed:** `ANGLE (Apple, ANGLE Metal Renderer: Apple
  M1)` on the measurement flags, on both rounds. `maxTextureSize=16384` on both
  platforms.

**The first round was not fair, and it was unfair in favour of `FULL`.** The
region replay extracted each box out of the shadow into a scratch buffer
**every frame** — 21 CPU copies — while the whole-chart replay uploaded its
shadow directly. That work belongs to the replay, not to the policy: the real
carrier packs a box from the chart once, at placement. The correction pre-packs
each region's buffer once at record time, so a measured frame issues GL calls
only for both policies.

The correction changed the answer, which is the point of recording it:

| | mean delta, first round | mean delta, corrected |
|---|---|---|
| JVM | +0.65 ms (**+12.0%**, region wins) | +0.12 ms (**+2.2%**) |
| Web | −1.59 ms (**−11.2%**, full wins) | −0.43 ms (**−3.8%**) |
| web per-cell spread | 8.2% / 12.6% | 3.6% / 2.7% |

## Numbers (corrected apparatus)

### JVM — `uncapped`, 640x360

| run | region | full |
|---|---|---|
| 1 | 6.03 ms (166 fps) | 6.17 ms (162 fps) |
| 2 | 5.60 ms (178 fps) | 5.45 ms (184 fps) |
| 3 | 5.33 ms (188 fps) | 5.71 ms (175 fps) |
| **mean** | **5.653 ms** | **5.777 ms** |

Delta +0.123 ms (+2.18%). Spreads 12.4% and 12.5%; the ranges overlap and the
per-run sign inverts (+2.3%, −2.7%, +7.1%).

### Web — `rAF` query mode, uncapped by flags, 640x360

| run | region | full |
|---|---|---|
| 1 | 11.55 ms | 10.85 ms |
| 2 | 11.14 ms | 10.73 ms |
| 3 | 11.20 ms | 11.02 ms |
| **mean** | **11.297 ms** | **10.867 ms** |

Delta −0.430 ms (−3.81%). Spreads 3.6% and 2.7%; the ranges are disjoint and
`FULL` is faster in all three runs, but the mean delta is inside the band.

## Verdicts

- **Neither platform distinguishes the two policies under this load.** Both mean
  deltas are inside the ~±5% band the lever round set for this apparatus, so
  neither is a win and neither is a regression. On the JVM the direction is
  inconsistent run to run; on the web it repeats, and that repetition is worth
  noting without promoting it to a result.
- **The traffic is not what costs at this load.** `FULL` sends one 512x512 upload
  per frame — 256 KB of `R8` on the JVM, 1 MB of `RGBA8` on the web — against
  roughly 2.8 KB / 11 KB of box traffic, and lands inside the noise band either
  way. At 21 boxes per frame the draw work dominates, not the bytes.
- **The engine as shipped already has the cheaper shape**: the region policy is
  paid once per placement, while the counterfactual pays a whole chart per frame
  that the atlas changed. The cell prices the upload calls; it does not price
  that asymmetry.

## What the cell excludes, by construction

- **All CPU preparation.** A measured frame issues GL calls only. The region
  policy packs a box once per placement; a whole-chart policy would additionally
  pay a chart-wide pack per frame (on the JVM, extracting the alpha channel from
  a 512x512 `Sprite`), or keep a retained GPU copy — a separate cost this cell
  does not price.
- **The atlas build itself**, whose region uploads are what the replay copies.
- **The deferred occupancy battery** of the sizing research (real-set occupancy
  at 256/512/1024, the chart-count effect), deferred by decision 6 with the
  closed `CHART_SIZE` decision.
