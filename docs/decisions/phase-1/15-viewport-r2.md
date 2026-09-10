## 2026-09-10 — R2 (viewport/clipping): touch-point decisions

Macro requirement (roadmap "Render"): pure clip math. Carries the C6 debt
(`drawLine` with an out-of-bounds endpoint) and covers the `Viewport.Bounded`
that S3 deferred (log #33). Open items decided at this touch-point.

### Verified facts (reference = olc v2.30, read directly from the source)

- **olc clips the line before walking.** `DrawLine` calls
  `ClipLineToDrawTarget` (Cohen–Sutherland) *before* the gurkanctn walk; a
  trivial reject (`s1 & s2`) draws nothing, otherwise the walk runs on the
  clipped endpoints.
- **The error uses the ORIGINAL deltas, the span uses the clipped ones.**
  `dx = x2 - x1; dy = y2 - y1` are computed before the clip; after the clip
  `x1..y2` are overwritten, but `dx1 = abs(dx); dy1 = abs(dy);
  px = 2*dy1-dx1; py = 2*dx1-dy1` still use the original `dx`/`dy`. So the
  reference is *original error state + step direction over the clipped span* —
  neither "walk full then drop" (current KGE) nor "recompute the error from
  the clipped segment".
- **The clip boundary is inclusive at width/height.** `Segment` flags
  `x > width` (not `>=`), so an endpoint exactly at `x == width` survives the
  clip, is walked, and is then dropped by `SetPixel`'s `x < width` bounds
  check. Integer division truncates toward zero in C++, which matches Kotlin
  `Int /` — parity requires reproducing this literally.
- **Only sloped lines carry debt.** Vertical/horizontal lines are
  axis-aligned, so "drop OOB" == "clip then walk" — identical cells. `drawRect`
  and `drawTriangle` inherit via `drawLine`. `fillRect` already clips to the
  exact intersection (C6, no debt). `drawCircle`/`fillCircle` are early-out +
  per-cell drop, matching olc's `Draw` bounds-check (no debt).
- **`main`'s `Viewport`/`BresenhamLine` is NOT the reference.** It clipped by
  `fittest` (clamp endpoint to bounds) + re-walk with recomputed deltas — a
  different clip than olc's Cohen–Sutherland. The reference (olc) is what is
  ported, not `main`.

### Decisions (owner)

- **The clip is a service (`ClipService : KGEOverridable`); the `Viewport`
  type is pure math.** (Revised 2026-09-10, owner.) Rationale: a better
  implementation may be found, and — decisively — *even more elementary
  behaviors are already pluggable*: `DrawService.draw`, the atomic per-pixel
  write every primitive resolves through, is a T2 seam. Consistency (principle
  1, "extensible by principle") demands the clip — also an observable
  raster behavior — be a seam too. The `Viewport` sealed type carries only the
  data (`contains` + the bounds); the clip *algorithm* is a T2 seam the engine
  declares and consumers override. The consumer is `OutlineService.drawLine`,
  which resolves the clip through `ClipService` per call — an override of
  `ClipService` is observed by `drawLine` (proven by the extension-contract
  test).
- **`ClipService` shape.** `clipLineTo(viewport: Viewport, start: Int2D, end:
  Int2D): Pair<Int2D, Int2D>?` — `null` on trivial reject, else
  `(clippedStart, clippedEnd)`. The default reproduces olc
  `ClipLineToDrawTarget` exactly. A **fifth raster sub-service** — the
  `Rasterizer` aggregate implements it (`ClipService by ClipService`, alongside
  `DrawService`/`OutlineService`/`FillService`/`DrawSpriteService`), so
  `Rasterizer.clipLineTo` resolves the active implementation. Package
  `rasterizer.service`.
- **Full sealed hierarchy.** `Unbounded` / `LowerBounded` / `UpperBounded` /
  `Bounded` + `contains`, in `main`'s shape. The three variants without an R2
  consumer are kept and **pinned by their own tests** (API discipline — no
  provisional API).
- **Clip semantics = olc, not `main`.** `main`'s `fittest`/`outCode`/
  `BresenhamLine` iterator are NOT ported (superseded, no consumer). The
  default clip reproduces olc `ClipLineToDrawTarget` exactly: integer
  Cohen–Sutherland, `Segment` boundary inclusive at `width`/`height`
  (`x < 0 → L`, `x > width → R`, `y < 0 → B`, `y > height → T`), truncating
  division, trivial reject on `s1 & s2`.
- **`Pixmap : Viewport.Bounded`** is reintroduced (as the old `PixelMap`):
  `lowerBoundInclusive = (0, 0)`, `upperBoundExclusive = (width, height)`.
  The `image → rasterizer` dependency this creates is accepted (as in `main`).
- **`drawLine` parity restoration.** In `OutlineService.drawLine`: compute
  original `dx`/`dy` first, clip via `ClipService.clipLineTo(target, ...)`,
  return on trivial reject, then walk gurkanctn with the original `dx`/`dy`
  (sign + `px`/`py` error) over the clipped span. Axis-aligned lines route
  through the same clip (already identical cells, kept for uniform parity).
- **Package: `rasterizer`** (as in `main`), despite `image` (Pixmap) consuming
  it.

### Follow-up

Micro-plan next (`docs/plans/2026-09-10-r2-viewport-microplan.md`), TDD per
step, then the usual gate/review. The drawLine parity step is micro-plan
step 4 (per the C6 close record, log #35).

## 2026-09-10 — R2 (viewport/clipping): closed

Micro-plan steps 0–5 delivered; the touch-point decisions above stand. Two-axis
review passed with only KDoc/link fixes (report in `.opencode/reviews/`,
machine-local). Decisions and findings recorded at close:

- **`Viewport` is pure data; the clip is the seam (as decided).** The sealed
  hierarchy carries only `contains` + the two bounds. The diamond default of
  `Bounded` must call `super<LowerBounded>.contains(...)` /
  `super<UpperBounded>.contains(...)`: the touch-point sketch's
  `(this as LowerBounded).contains(...)` recurses via virtual dispatch and
  stack-overflows. `contains` is exclusive at the upper bound (the per-pixel
  write convention) while the clip boundary is inclusive (olc), so a point can
  be "not contained" yet survive the clip and be dropped by `DrawService` — the
  two predicates are deliberately distinct.
- **`ClipService` default reproduces olc `ClipLineToDrawTarget` exactly**
  (integer Cohen–Sutherland, boundary inclusive at the upper bound, `s3 =
  max(s1, s2)` numeric pick, T/B/R/L `else if` order, truncating division,
  trivial reject on `s1 & s2`). `Unbounded` returns the segment unchanged; the
  partial variants share one `clip(lower, upper, ...)` helper with `null` for
  the absent bound (L/B, R/T). Micro-plan corrections: the hypothesized
  truncation case `(3,5)→(-1,1)` does not truncate (`(2,4) to (0,2)`, kept as a
  T-then-L case); the real truncation case is `(-1,3)→(2,1)` (`-2/3 → 0`,
  `(0,3) to (2,1)`).
- **`Pixmap : Viewport.Bounded`** with `lower = (0,0)`, `upper =
  (width, height)`; the accepted `image → rasterizer` dependency. The bound test
  lives in `image/RasterizerClipTest` (step 3 pinned no file).
- **`drawLine` clip-then-walk restores olc parity and clears the C6 debt.**
  Original `dx`/`dy` feed the gurkanctn error/step, the walk runs over the
  clipped span (inclusive). The micro-plan's "top-edge steep" case is not
  debt-bearing (its walk starts at the interior endpoint); the debt-bearing
  shape is a line whose walk *starts* OOB — pinned by a bottom-entering steep
  case `(1,-2)→(2,3)` → `(1,0),(1,1),(1,2),(2,3)` vs the old
  `(1,0),(2,1),(2,2),(2,3)`. `RasterizerTest`'s "crossing the edge" case pinned
  the old debt and was corrected to `(0,0),(0,1)`.
- **`Rasterizer` aggregates five sub-services** (`ClipService by ClipService`
  added); the extension-contract test proves an override is observed by
  `drawLine` and `Rasterizer.clipLineTo`, and `resetAll` restores the default.
- **Gate:** `./gradlew build --rerun-tasks` green (all targets, ktlint,
  metadata/assemble).

## 2026-09-10 — R2 post-close: owner review of the staged diff (Hunk)

Owner inline review of the staged R2 diff, resolved before the close commit:

- **`Int2D.ZERO` constant (accepted).** `Pixmap.lowerBoundInclusive` allocated
  `Int2D(0, 0)` on every access; the origin is a shared `companion` constant
  (`Int2D.ZERO`), pinned by a test.
- **`ClipService` default as a scoped object (accepted).** The default was a
  top-level `private val ... = object : ClipService` with the Cohen–Sutherland
  `SEG_*` flag constants and the `clip`/`outCode` helpers at file scope. It is
  now a `private object ClipServiceDefault : ClipService` with the constants and
  helpers scoped inside it (the `HexPixelFormat` precedent).
- **Full `Pixmap` bounds — deferred to a post-R2 session (owner ruling).**
  `Pixmap : Viewport.Bounded` as shipped fixes `lowerBoundInclusive = (0,0)`
  and treats `width`/`height` as the source of truth. The owner judges that too
  restrictive: a `Pixmap` that is a *window* over another needs an arbitrary
  inclusive lower bound and dimensions derived from the bounds, with the whole
  raster stack reading the bounds instead of assuming an origin at `(0,0)`.
  This is an accepted debt (as the C6 debt was carried to R2) and is scheduled
  as the third post-R2 session in the roadmap; it is **not** in R2's scope
  (R2's own out-of-scope note already excludes user-viewports until C9). The
  touch-point for that session decides the coordinate convention (absolute
  bounds vs a local space with an origin) and read/write mutability of a view.
