# R2 viewport/clipping micro-plan

**Date:** 2026-09-10. Touch-point decisions in
`docs/decisions/phase-1/15-viewport-r2.md` (the reference facts and the owner
rulings). Concept: the pure `Viewport` sealed type + the `ClipService` seam +
the `Pixmap : Viewport.Bounded` reintroduction + the `OutlineService.drawLine`
clip-then-walk parity fix that clears the C6 partial-OOB debt.

Executed step-by-step: compare → decide → test (red) → implement (green on
jvm+js+wasmJs) → mark. Per-step expectations are hypotheses; a contradicting
finding overrides them and is recorded in the step's `Decided:` field.

## Decisions (owner, 2026-09-10 — see the touch-point chunk)

- **The clip is a service (`ClipService : KGEOverridable`); the `Viewport`
  type is pure math.** The `Viewport` sealed type carries only the data
  (`contains` + the bounds); the clip *algorithm* is a T2 seam. Rationale:
  even more elementary behaviors (`DrawService.draw`, the per-pixel write) are
  already pluggable — consistency (principle 1) demands the clip be too.
- **Full sealed hierarchy.** `Unbounded` / `LowerBounded` / `UpperBounded` /
  `Bounded` + `contains`, in `main`'s shape. The three variants without an R2
  consumer are pinned by their own tests (API discipline).
- **Clip semantics = olc, not `main`.** `main`'s `fittest`/`outCode`/
  `BresenhamLine` are NOT ported. The default clip reproduces olc v2.30
  `ClipLineToDrawTarget` exactly (integer Cohen–Sutherland, `Segment` boundary
  inclusive at width/height, truncating division, trivial reject on `s1 & s2`).
- **`Pixmap : Viewport.Bounded`** reintroduced (`lower=(0,0)`,
  `upper=(width,height)`); `image → rasterizer` dependency accepted.
- **`drawLine` parity**: compute original `dx`/`dy` first, clip via
  `ClipService`, reject ⇒ return, then walk gurkanctn with original `dx`/`dy`
  over the clipped span.
- **Package: `rasterizer`** (the `Viewport` type); `rasterizer.service` (the
  `ClipService`).

## Contract (decided; shapes may tighten per step)

`sealed interface Viewport` (package `rasterizer`):

- variants: `LowerBounded` (`lowerBoundInclusive: Int2D`), `UpperBounded`
  (`upperBoundExclusive: Int2D`), `Bounded : LowerBounded, UpperBounded`,
  `data object Unbounded`.
- `contains(x, y): Boolean` — **exclusive upper** (`x < upper.x`, `y <
  upper.y`), the `main` convention; `contains(p: Int2D)` = `contains(p.x,
  p.y)`. No clip method on the type (the clip is the service).

`interface ClipService : KGEOverridable` (package `rasterizer.service`):

- A fifth raster sub-service: the `Rasterizer` aggregate implements it
  (`ClipService by ClipService`), so `Rasterizer.clipLineTo` resolves the
  active implementation.
- `clipLineTo(viewport: Viewport, start: Int2D, end: Int2D): Pair<Int2D,
  Int2D>?` — `null` on trivial reject, else `(clippedStart, clippedEnd)`.
- Default dispatches on the `viewport` variant. `Bounded` reproduces olc's
  Cohen–Sutherland with **inclusive** boundaries: `Segment` flags
  `x < lower.x → L`, `x > upper.x → R`, `y < lower.y → B`, `y > upper.y → T`
  (a coordinate exactly on `upper` survives the clip). Intersection uses
  `n.x = p1.x + (p2.x - p1.x) * (boundary - p1.y) / (p2.y - p1.y)` (and the
  transpose), integer division truncating toward zero; the outcode pick is
  `s3 = if (s2 > s1) s2 else s1`. `Unbounded` = `start to end` (never
  rejects); `LowerBounded`/`UpperBounded` clip only their bounded axes (L/B
  and R/T respectively).

`Pixmap` gains `: Viewport.Bounded` with `lowerBoundInclusive = Int2D(0, 0)`
and `upperBoundExclusive = Int2D(width, height)` (interface defaults in
`Pixmap`; `Sprite` inherits).

`OutlineService.drawLine` default: `val dx = x1 - x0; val dy = y1 - y0`, then
`val (s, e) = ClipService.clipLineTo(target, Int2D(x0, y0), Int2D(x1, y1))
?: return`, then the existing gurkanctn walk over `s.x..e.x` (span) with
`dx`/`dy` (original) for branch, step direction and `px`/`py` error — the
exact olc shape. The axis-aligned short-circuits now iterate the clipped range
(same visible cells, tighter loop).

## Files

- New (commonMain): `rasterizer/Viewport.kt`,
  `rasterizer/service/ClipService.kt`.
- Modified: `image/Pixmap.kt` (adds `: Viewport.Bounded` + the two bound
  getters), `rasterizer/Rasterizer.kt` (adds `ClipService by ClipService`),
  `rasterizer/service/OutlineService.kt` (clip-then-walk in the default
  object, resolving the clip through `ClipService`).
- New (commonTest): `rasterizer/ViewportTest.kt` (type + `contains`),
  `rasterizer/ClipServiceTest.kt` (the default clip + the seam), and the
  drawLine parity cases in `image/RasterizerClipTest.kt`, using the existing
  `target()` blank-canvas helper.

## Steps

### 0. `Viewport` type + `contains`

- [x] **Test (red):** `ViewportTest` — the four variants exist;
  `Unbounded.contains(any, any)` is always true; `Bounded`-like
  (`lower=(1,2)`, `upper=(4,5)`) `contains`: (1,2) true, (3,4) true, (4,5)
  false (exclusive upper), (0,2) false, (1,5) false; `LowerBounded`-only
  (`lower=(1,2)`) contains (1,2) true, (0,0) false, (1000,1000) true;
  `UpperBounded`-only (`upper=(4,5)`) contains (0,0) true, (4,5) false,
  (-1000,-1000) true; `contains(p: Int2D)` equals `contains(p.x, p.y)`.
- [x] **Run:** fails (type missing).
- [x] **Implement:** the sealed interface + variants + default `contains`.
- [x] **Run:** green on jvm/js/wasmJs.
- [x] **Decided:** the supplied `Bounded.contains` body
  `(this as LowerBounded).contains(x, y) && (this as UpperBounded).contains(x, y)`
  stack-overflows: the cast does not select the super default, virtual dispatch
  recurses back into `Bounded.contains`. The diamond default must be
  `super<LowerBounded>.contains(x, y) && super<UpperBounded>.contains(x, y)`.
  Test-local `object : Viewport.Bounded/LowerBounded/UpperBounded` instantiation
  compiles (nested interfaces are ordinary; only the direct sealed subtypes are
  module-restricted).

### 1. `ClipService` default on `Bounded` — olc parity

- [x] **Test (red):** `ClipServiceTest` pins the exact olc semantics on a
  `Bounded(lower=(0,0), upper=(4,4))`:
  - fully inside `(1,1)→(3,2)` returns `(1,1) to (3,2)`;
  - trivial reject `(-1,-1)→(-2,-2)` (and `(5,5)→(6,6)`) returns `null`;
  - left-crossing `(-3,-1)→(3,2)` returns `(0,0) to (3,2)` (hand-derived:
    B-clip → `(-1,0)`, then L-clip → `(0,0)` with `2/4` truncating to 0);
  - top-crossing with the `x == upper` inclusive case: `(1,6)→(1,2)` returns
    `(1,4) to (1,2)` (the boundary at `y == 4` is NOT clipped — survives;
    only the `y > 4` part is cut);
  - integer truncation: a case whose intersection fraction truncates toward
    zero (negative numerator too, e.g. `(3,5)→(-1,1)` clips at a negative
    fractional coordinate) — pin the exact integers.
- [x] **Run:** fails.
- [x] **Implement:** the olc Cohen–Sutherland in the default (inclusive `>`
  boundaries, `s3 = max` pick, truncating division).
- [x] **Run:** green on all targets.
- [x] **Decided:** the plan's hypothesized truncation example `(3,5)→(-1,1)`
  does **not** truncate: it clips exactly to `(2,4) to (0,2)` (T then L, both
  fractions integral). It is kept as a T-then-L parity case. A genuine
  truncation-toward-zero case with a negative numerator is `(-1,3)→(2,1)`,
  whose L intersection is `-2/3 → 0`, giving `(0,3) to (2,1)`.

### 2. `ClipService` on `Unbounded`/`LowerBounded`/`UpperBounded`

- [x] **Test (red):** `Unbounded` returns the segment unchanged and never
  `null`; `LowerBounded(lower=(2,2))` clips only the lower axes (a
  `(-1,3)→(5,3)` → `(2,3)→(5,3)`; a `(-1,-1)→(-2,-2)` → `null`);
  `UpperBounded(upper=(4,4))` clips only the upper axes (a `(1,1)→(6,1)` →
  `(1,1)→(4,1)`; a `(5,5)→(6,6)` → `null`).
- [x] **Run:** fails → implement the partial clips → green on all targets.
- [x] **Decided:** the partial variants share one `clip(lower, upper, ...)`
  helper by passing `null` for the absent bound; `Unbounded` bypasses it and
  returns the segment unchanged. No contradiction.

### 3. `Pixmap : Viewport.Bounded`

- [x] **Test (red):** a 4×3 `Sprite` has `lowerBoundInclusive == Int2D(0, 0)`
  and `upperBoundExclusive == Int2D(4, 3)`; `target.contains(3, 2)` true,
  `target.contains(4, 2)` false.
- [x] **Run:** fails (`Pixmap` not a `Viewport.Bounded`).
- [x] **Implement:** `Pixmap : Viewport.Bounded` + the two default bound
  getters (from `width`/`height`).
- [x] **Run:** green on all targets.
- [x] **Decided:** placed the bound test in the new `image/RasterizerClipTest`
  (the plan did not pin a file for step 3; it shares that file's `target()`
  helper). `Sprite` inherits the defaults through `MutablePixmap`/`Pixmap`.

### 4. `drawLine` clip-then-walk parity (clears the C6 debt)

- [x] **Test (red):** on a 4×4 `Sprite`, `drawLine(-3, -1, 3, 2)` paints
  exactly `(0,0),(1,1),(2,1),(3,2)` — the reference clip re-walk, which
  differs from the current unclipped-walk-subset `(0,1),(1,1),(2,2),(3,2)`
  (this red proves the debt). Add: a trivial-reject line paints nothing and
  never throws; a line entering from the top edge (steep) pins the clipped
  span; a partial-OOB vertical and horizontal line still paint the visible
  range (already parity, kept as regression).
- [x] **Run:** fails (current walk paints the wrong cells).
- [x] **Implement:** the clip-then-walk in `OutlineService.drawLine` via
  `ClipService` (original `dx`/`dy` for error, clipped span; reject ⇒ return).
- [x] **Run:** green on all targets.
- [x] **Decided:** the plan's "top-edge steep" case is **not** debt-bearing: a
  top-entering steep line's walk starts at the interior (lower-`y`) endpoint,
  so the clip only truncates the already-dropped OOB tail and the visible cells
  are unchanged. The debt-bearing steep shape is a line whose walk *starts* at
  the OOB endpoint; the test uses bottom-entering `(1,-2)→(2,3)`
  (`(1,0),(1,1),(1,2),(2,3)` vs the old `(1,0),(2,1),(2,2),(2,3)`). The
  existing `RasterizerTest` "drawLine crossing the edge" case pinned the old
  debt and was updated from `(1,0),(1,1)` to the olc-parity `(0,0),(0,1)`.
  A cell clipped exactly onto `x == width`/`y == height` survives the clip but
  is correctly dropped by the `DrawService` bounds check.

### 5. `ClipService` extension-contract (the seam is observable)

- [x] **Test (red):** a registered decorator overriding `ClipService.clipLineTo`
  (delegating to `original`) is observed by `drawLine`: e.g. a decorator that
  rejects every line whose `start.x < 0` (returns `null` there, delegates
  otherwise) makes `drawLine(-3,-1,3,2)` paint nothing, while a fully-inside
  line still paints; the same override is observed through
  `Rasterizer.clipLineTo(...)` (the aggregate delegates to the active
  implementation); `KGEOverridable.Proxy.resetAll()` restores the default
  (the `(-3,-1)→(3,2)` cells return). Mirrors the existing
  `KGEOverridableExtensionTest` and raster `drawDelegate`/`outlineDelegate`
  idioms.
- [x] **Run:** fails (no `ClipService` seam yet) → implement → green on all
  targets.
- [x] **Decided:** no contradiction. `Rasterizer` gains
  `ClipService by ClipService` (fifth delegation); the seam test's decorator is
  `object : ClipService by original { ... }`; the override is observed both
  through the aggregate facade and through `drawLine`, and `resetAll` restores
  the default.

### 6. Gate + close

- [x] `./gradlew build --rerun-tasks` green (all suites + ktlint). API audit:
  every public member (`contains` both forms, `clipLineTo`, the two bound
  getters, every `Viewport` variant, the `ClipService` seam) has an observable
  effect pinned by a test; no allocation added to the raster hot paths (clip
  runs once per `drawLine`, before the walk).
- [x] Two-axis review (standards + spec), fixes, verify pass of the delta.
- [x] Decisions-log entry (per-step `Decided:` + owner rulings) + roadmap
  update (mark R2 closed, refresh "Current state", note the C6 debt cleared);
  touch-point chunk marked consumed.
- [x] **Decided:** the review found no hard misses; only KDoc fixes: the
  `drawLine` KDoc now states the clipped-span + original-delta walk, and a
  broken `[drawLine]` link in `ClipService` retargets to
  `OutlineService.drawLine`. A `x == upper` end-to-end cell test was declined:
  the inclusive-survival cell is indistinguishable from clipping at `upper - 1`
  once `DrawService` drops it, so it adds no observable coverage beyond the
  `ClipServiceTest` boundary case. Step-3 bound test pinned in
  `image/RasterizerClipTest`.

## Out of scope

`LinePattern` (roadmap out-of-scope — the `rol()` pattern roll is ignored, as
in C6); `main`'s `fittest`/`outCode`/`BresenhamLine` iterator (superseded by
the olc clip); any viewport *other than the full draw-target* on the raster
primitives (a user-viewport parameter has no consumer until the renderer/layer
concept — C9); `fillRect`/`fillCircle`/`fillTriangle`/`drawSprite`/circles
(already parity or no debt, unchanged); `DrawService` (unchanged — it remains
the per-pixel seam that drops the `x == upper` clip boundary cells, matching
olc's `SetPixel` bounds check).
