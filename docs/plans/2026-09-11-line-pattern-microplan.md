# Line patterns micro-plan (interactive execution)

**Date:** 2026-09-11. Touch-point decisions in
`docs/decisions/phase-1/18-line-pattern.md`. Concept: `OutlineService.drawLine`
gains a required `LinePattern` deciding which walked cells are painted, and
`drawRect`/`drawTriangle` propagate the same pattern instance to their edges.

## Working mode

- **No subagents.** One step = compare → decide → test (red) → implement (green
  on jvm+js+wasmJs) → mark. Doubts resolved in-session with the owner.
- **Oracle = olc v2.30 + `main`.** The pattern **phase** follows olc (consumed
  per walked cell from the first cell of the clipped walk); the **type and propagation** follow
  `main`'s sealed `LinePattern`. `ALL`-equivalent is `Filled`.
- **`Filled` is the hard invariant.** With `Filled`, `drawLine`/`drawRect`/
  `drawTriangle` must stay pixel- and write-count-identical to the current
  behavior under every mode.
- **Sources:** olc v2.30 `DrawLine` (`.tmp/olc/olcPixelGameEngine.h:2773-2854`);
  `main` = `git show main:.../rasterizer/service/DrawLineService.kt` and
  `.../utils/BresenhamLine.kt`.

## Resume tracker (a new session starts at the first unchecked step)

- [x] **0. `LinePattern` type** (`rasterizer/LinePattern.kt`: `Empty`, `Filled`, `Dotted`, `Custom`)
- [x] **1. `drawLine` pattern** (raw + `Int2D` + `Rasterizer` forward; per-cell consumption from the first cell of the clipped walk)
- [x] **2. `drawRect`/`drawTriangle` propagation** (+ `drawFarthestPairLine`; update 71 call sites and decorators)
- [x] **3. Gate + review + decisions-log close**

## Contract (touch-point)

- `LinePattern` is a top-level `sealed interface` in
  `dev.staticsanches.kge.rasterizer` with `fun shouldDrawPixel(): Boolean`:
  `data object Empty` (false), `data object Filled` (true),
  `class Dotted(private var current: Boolean = true)` (returns the pre-toggle
  value, so the first cell is drawn, then alternating), `interface Custom :
  LinePattern`. Stateful `Dotted`/`Custom` are single-use by design; the caller
  owns the instance.
- Required parameter, no default, after `color`, before `mode`:
  `drawLine(target, x0, y0, x1, y1, color, pattern, mode)`,
  `drawLine(target, start: Int2D, end: Int2D, color, pattern, mode)`,
  `drawRect(target, x0, y0, x1, y1, color, pattern, mode)`,
  `drawTriangle(target, x0, y0, x1, y1, x2, y2, color, pattern, mode)`, and the
  `Int2D` forms. `Rasterizer` forwards.
- **Consumption:** one `shouldDrawPixel()` per walked cell, in walk order, in all
  three walk shapes (vertical, horizontal, general), starting at the first cell
  of the clipped walk (clipped start increasing / clipped end decreasing /
  geometric minimum for axis walks). `Empty` short-circuits before the walk;
  `Filled` takes the existing fast path with no `shouldDrawPixel()` call.
- **Propagation:** the same instance is passed to every edge of a rect/triangle,
  so a `Dotted` phase continues across the edges. `drawTriangle`'s collinear
  branch draws its single farthest-pair line with the given pattern;
  `fillTriangle`'s collinear branch uses `LinePattern.Filled`.
- `fillCircle`/`drawCircle`/`fillRect`/`fillTriangle` are unchanged.

## Steps

### 0. `LinePattern` type

- **Tests (red first):** `Filled.shouldDrawPixel()` true repeatedly;
  `Empty` false repeatedly; a fresh `Dotted` yields `true, false, true, …` across
  calls; a `Custom` implementation is honored. `Dotted`/`Custom` carry state
  across calls (documented by the alternating test).
- **Files:** new `rasterizer/LinePattern.kt`.
- **Decided:** done 2026-09-11 — `main`'s sealed `LinePattern` ported verbatim
  (top-level `rasterizer`): `Empty`, `Filled`, stateful `Dotted(current = true)`,
  `Custom`; single-use/caller-owned by design. `LinePatternTest` pins the set
  behavior incl. `Dotted(false)`'s phase.

### 1. `drawLine` pattern

- **Compare:** olc `DrawLine` (2773-2854: `rol()` before the first draw and once
  per subsequent cell, in each of the vertical/horizontal/general branches) vs
  the current clip-then-walk. Confirm the current walk's cell order matches so
  consuming a bit per cell from the first cell of the clipped walk reproduces olc's phase.
- **Tests:** `Filled` == the pre-change line (all existing line cells, all
  modes); `Empty` paints nothing; a `Dotted` line alternates along the walk with
  the first **visible** cell drawn; clipping shifts the phase (a line clipped at
  the left starts its pattern at the first cell of the clipped walk — pin a case where the
  unclipped and clipped phases differ); each walk shape (vertical, horizontal,
  general) consumes the pattern; `Mask`/`Alpha` compose (skipped cells are not
  blended). `Int2D` overload and `Rasterizer` forward the pattern.
- **Update call sites:** all `drawLine` call sites in `RasterizerTest`,
  `RasterizerPointOverloadTest`, `RasterizerClipTest` to pass
  `LinePattern.Filled`; update the `drawLine` decorator overrides.
- **Decided:** done 2026-09-11 — required `pattern` after `color` on the raw +
  `Int2D` forms; `Empty` returns before the clip, `Filled` runs the untouched
  walk with no `shouldDrawPixel()` (inline `if (filled || pattern.shouldDrawPixel())`,
  no closure/allocation), otherwise one call per walked cell from the first cell
  of the clipped walk in all three walk shapes. Tests cover `Filled` parity,
  `Empty`, `Dotted` alternation incl. decreasing and steep sub-branches, the
  decreasing/clipped phase, `Mask`/`Alpha`/`Custom` composition, and forwarding.
  Review-fix round removed the capturing closure on the `Filled` path
  (bytecode-verified: no `invokedynamic`).

### 2. `drawRect`/`drawTriangle` propagation

- **Compare:** `main`'s `DrawRect`/`DrawTriangle` addons pass the same pattern to
  each edge; olc has no pattern there.
- **Tests:** `drawRect`/`drawTriangle` with `Filled` == the pre-change sets; a
  `Dotted` rect continues its phase across all four edges (pin the exact drawn
  cells around the ring); a `Dotted` triangle across three edges; collinear
  `drawTriangle` uses the pattern for its single line; `Empty` rect/triangle
  paints nothing. The `Int2D` overloads forward.
- **`drawFarthestPairLine`:** add a `pattern` parameter; `drawTriangle` passes
  the caller's pattern, `fillTriangle` passes `LinePattern.Filled` (so fill is
  unchanged).
- **Update call sites:** all `drawRect`/`drawTriangle` call sites and decorators
  in the tests.
- **Decided:** done 2026-09-11 — `drawRect`/`drawTriangle` take `pattern` and
  pass the same instance to every edge (state continues), pinned incl. a
  collapsed/overlapping-edge `Dotted` rect; `drawFarthestPairLine` parameterized
  (`drawTriangle` collinear = the caller's pattern, `fillTriangle` collinear =
  `Filled`, so fill is unchanged); all call sites/decorators updated.

### 3. Gate + review + close

- **Gate:** `./gradlew build --rerun-tasks` green (all targets + ktlint +
  metadata/assemble). Leak audit (no allocations) + no-parameter-without-
  observable-effect audit. Two-axis review (Standards + Spec, two fresh general
  sub-agents). Decisions-log close entry.
- **Decided:** done 2026-09-11 — gate green; the two-axis review (two fresh
  sub-agents) found no Hard findings; one fix round (inlined the `Filled` gate to
  drop the closure/allocation, `Dotted` KDoc precision, corrected the
  "clipped start" wording to "first cell of the clipped walk", added
  decreasing/steep/`Custom`/collapsed-edge tests); verify pass clean. Close entry
  in `docs/decisions/phase-1/18-line-pattern.md`.

## Files

New: `rasterizer/LinePattern.kt`, `commonTest` `LinePatternTest.kt`. Modified:
`rasterizer/service/OutlineService.kt` (pattern on `drawLine`/`drawRect`/
`drawTriangle` + `drawFarthestPairLine`), `commonTest` `RasterizerTest.kt`,
`RasterizerPointOverloadTest.kt`, `RasterizerClipTest.kt` (call sites +
decorators), plus any other test file touching the three methods.

Out of scope: a custom draw-line pattern beyond `LinePattern`; circle/rect-fill/
triangle-fill patterns; the addon API that will default `pattern = Filled` (C10).
