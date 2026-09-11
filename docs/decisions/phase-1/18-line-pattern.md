## 2026-09-11 — Line patterns: touch-point decisions

`R1` raster widening (owner decision, scheduled right after the circle octant
masks; roadmap). Macro requirement: `OutlineService.drawLine` accepts a
`LinePattern` deciding which walked cells are painted, and `drawRect`/
`drawTriangle` propagate the same pattern into their edge `drawLine` calls.
`fillCircle`/`drawCircle` are untouched (circles use `CircleOctantMask`, not a
pattern).

### Verified facts (olc v2.30, main, current code)

- **olc v2.30 `DrawLine(x1, y1, x2, y2, p, uint32_t pattern = 0xFFFFFFFF)`**
  (`.tmp/olc/olcPixelGameEngine.h:2773-2854`): a 32-bit **rotating** mask —
  `rol()` = `pattern = (pattern << 1) | (pattern >> 31); return pattern & 1` —
  consumed once per walked cell; `0xFFFFFFFF` = solid, `0x00000000` = nothing.
  `ClipLineToDrawTarget` runs **before** the walk, so the pattern is consumed
  from the first cell of the clipped walk — the clipped start when the walk
  runs in increasing order, the clipped end when it runs in decreasing order
  (and the geometric minimum for the axis lines). The special vertical/horizontal
  branches also call `rol()` once per cell. `DrawRect`/`DrawTriangle` in olc
  have **no** pattern.
- **`main` typed it** (`DrawLineService.LinePattern`, `git show main:...`): a
  `sealed interface` with `shouldDrawPixel(): Boolean` — `data object Empty`
  (never), `data object Filled` (always), `class Dotted(private var current =
  true)` (stateful alternation, first cell drawn), `interface Custom :
  LinePattern`. `main` **propagated** the pattern to `drawRect`/`drawTriangle`/
  addons (the same instance flows through every edge). `main`'s `BresenhamLine`
  clips via `Viewport` before iterating, so the pattern is consumed from the
  first cell of the clipped walk there too.
- **Current `drawLine`** (`OutlineService.kt`) is the R2 clip-then-walk
  (original deltas, clipped span; vertical/horizontal/single-point special
  cases); `drawRect` = four active-`drawLine` calls, `drawTriangle` = three
  (collinear → `drawFarthestPairLine`, shared with `fillTriangle`).
- **71 test call sites** across 5 files pass no pattern; adding a required
  parameter updates them all (mechanical).

### Decisions (owner)

- **Scope: propagate.** `drawLine`, `drawRect` and `drawTriangle` all take the
  pattern (main parity, not olc's line-only). The **same `LinePattern`
  instance** flows through the edges of a rect/triangle, so a stateful pattern
  (`Dotted`/`Custom`) continues its phase across edges — main's behavior.
  `drawCircle`/`fillCircle`/`fillRect`/`fillTriangle` are unaffected; the
  `fillTriangle` collinear branch (via `drawFarthestPairLine`) uses
  `LinePattern.Filled`.
- **Type: `main`'s sealed `LinePattern`** (stateful `shouldDrawPixel()`),
  top-level in package `rasterizer` (`rasterizer/LinePattern.kt`): `Empty`,
  `Filled`, `Dotted(current = true)`, `Custom`. Accepted non-purity: a `Dotted`/
  `Custom` instance is single-use / carries state across the cells and edges it
  is passed to; the caller owns the instance.
- **Required parameter, no default** (repo pattern), positioned after `color`
  and before `mode` (olc/`main` placement):
  `drawLine(target, x0, y0, x1, y1, color, pattern, mode)`; the `Int2D` forms
  `(target, start, end, color, pattern, mode)`; `drawRect`/`drawTriangle` add
  the same parameter. `Rasterizer` forwards.
- **Clip phase follows olc:** the pattern is consumed per walked cell from the
  **first cell of the clipped walk** (the clipped start for an increasing walk,
  the clipped end for a decreasing general line, the geometric minimum for the
  axis lines; olc sorts axis lines ascending and picks the far end for a
  decreasing general line). `Empty` short-circuits before the walk; `Filled`
  takes the existing fast path with no `shouldDrawPixel()` call.
- **Per-cell consumption, including axis walks.** One `shouldDrawPixel()` call
  per walked cell in all three walk shapes (vertical, horizontal, general),
  matching olc's `rol()` placement.

### Open for the micro-plan

- Exact handling in `drawFarthestPairLine` (parameterize with a pattern for the
  `drawTriangle` collinear branch, `Filled` for `fillTriangle`).
- The `Filled`/`Empty` fast-path structure and where the pattern is consumed
  relative to the first cell of each special-case loop.
- KDoc and test inventory: `Dotted` alternation incl. phase across rect edges;
  `Custom`; `Empty`; `Filled` parity with the pre-change line; the phase from
  the first cell of the clipped walk (a clipped dotted line's first visible cell
  uses the pattern's first bit);
  per-mode blending composition; the 71 call-site updates + decorator
  signatures.

Micro-plan next: `docs/plans/2026-09-11-line-pattern-microplan.md`, TDD per
step, then the usual gate/review.

## 2026-09-11 — Line patterns: closed

Micro-plan delivered. Final shape:

- **`LinePattern`** (`rasterizer`, top-level): `main`'s sealed interface ported
  verbatim — `data object Empty`/`Filled`, stateful `class Dotted(private var
  current = true)`, `interface Custom`. Stateful patterns are single-use and
  caller-owned. KDoc: the first `shouldDrawPixel()` returns the initial
  `current`, defaulting to `true` (painted).
- **Required `pattern` after `color`** on `drawLine`/`drawRect`/`drawTriangle`
  (raw + `Int2D`, forwarded by `Rasterizer`); no default on the service seam
  (the addon `Filled` default is C10).
- **Consumption: one `shouldDrawPixel()` per walked cell**, in walk order, from
  the **first cell of the clipped walk** — the clipped start for an increasing
  walk, the clipped end for a decreasing one, the geometric minimum for the
  axis walks (olc's clip-then-walk phase). `Empty` short-circuits before the
  clip; `Filled` runs the untouched walk with no `shouldDrawPixel()` call.
- **Propagation:** the same instance flows through every edge of a rect/
  triangle, so a `Dotted`/`Custom` phase continues across edges (pinned incl. a
  collapsed/overlapping-edge rect). `drawTriangle`'s collinear branch uses the
  caller's pattern; `fillTriangle`'s collinear branch uses `Filled` (fill
  unchanged). `drawCircle`/`fillCircle`/`fillRect`/`fillTriangle` signatures
  unchanged.
- **Review (two axes, one fix round, no hard findings).** Resolved: the `Filled`
  path had routed cells through a capturing closure — replaced by an inline
  `if (filled || pattern.shouldDrawPixel())` (bytecode-verified: no
  `invokedynamic`/allocation); `Dotted`'s KDoc made precise; the "clipped start"
  wording corrected to "first cell of the clipped walk" in the KDoc, the
  touch-point, the micro-plan and the index; added the decreasing-direction,
  steep sub-branch, `Pixel.Mode.Custom` and collapsed-edge `Dotted` tests. The
  verify pass was clean.
- **Gate:** `./gradlew build --rerun-tasks` green (all targets + ktlint +
  metadata/assemble).
