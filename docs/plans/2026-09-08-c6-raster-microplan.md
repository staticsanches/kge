# C6 (raster ops — R1) micro-plan (rev 2 — interactive execution)

**Date:** 2026-09-08. Touch-point decisions in `docs/decisions/phase-1.md` (this
session). Concept: CPU primitives over a `MutablePixmap`, the pixel modes +
blend math (log #24) and `Flip` (C5 deferral), plus the native bulk
`fillInts`/`copyInts` overrides (log #31). Scope is unchanged from the
touch-point; **rev 2 changes only the execution model and the oracle method.**

## Working mode (rev 2)

- **No subagents.** Executed step-by-step in-session with the owner: one step =
  compare → decide → test (red) → implement (green on jvm+js+wasmJs) → mark.
  Doubts are resolved as they arise, not delegated.
- **Oracle per primitive = main × olc v2.30, decided case by case.** The roadmap
  mandates olc v2.30 semantics; `main` is the battle-tested Kotlin port ("did it
  work?"). Neither is a silent mandate: each step opens the two implementations
  side by side, decides the exact pixel behavior, and pins it in the test. A
  deliberate deviation from either is a decision recorded in the step's
  `Decided:` field and later in the decisions log. **Fix a hypothesis first** —
  the micro-plan's test expectations below are hypotheses from `main`; a compare
  finding that contradicts them overrides the hypothesis.
- **Sources:** olc v2.30 = `olcPixelGameEngine.h` cached at
  `.tmp/olc/olcPixelGameEngine.h` (fetched 2026-09-08, tag `v2.30`,
  raw.githubusercontent.com/OneLoneCoder/olcPixelGameEngine/v2.30/; re-fetch on
  corruption). `main` = `git show main:kge-core/src/commonMain/kotlin/
  dev/staticsanches/kge/<path>`. KGE paths below are relative to that prefix.

## Resume tracker (update in-session; a new session starts at the first unchecked step)

- [x] **0. `Sprite.byteBuffer` public + `@KGESensitiveAPI`** (C5 widening, touch-point)
- [x] **1. `Pixel.Mode` + blend math** (Alpha/Mask/Normal/Custom, clamp, result alpha)
- [x] **2. Funnel `draw`** (mode resolution + OOB policy; point-draw visibility)
- [x] **3. `fillRect`** (inclusive, clip, inverted diagonal, NORMAL row fast path)
- [x] **4. `drawLine`** (algorithm × olc "gurkanctn"; inclusive; clip policy)
- [x] **5. `drawRect` / `drawCircle` / `fillCircle`** (+ drawRect diagonal rule)
- [x] **6. `fillTriangle`** (half-open tie rule + independent oracle + differential)
- [x] **7. `drawTriangle`** (outline = 3 lines; collinear; degenerate)
- [x] **8. `drawSprite` + `Flip` + scale** (fast vs block path, flips, alpha modes)
- [x] **9. Seam extension-contract** (decorator overrides `fillRect`, rest delegates)
- [x] **10. Native bulk `fillInts`/`copyInts`** (expect/actual, jvm + web)
- [x] **11. Gate + decisions-log close**

## Contract (decided at touch-point; shapes may still tighten per step)

`Pixel.Mode` (Normal verbatim / Mask a==255 / Alpha(blendFactor) / Custom) and
`Sprite.Flip` (NONE/HORIZONTAL/VERTICAL/BOTH) nest on their types. One seam —
`RasterizerService : KGEOverridable` in a single file, all helpers private:
`draw`, `drawLine`, `drawRect`, `fillRect`, `drawCircle`, `fillCircle`,
`drawTriangle`, `fillTriangle`, `drawSprite`, per the touch-point signature
block. Blend in the draw seam: `a = (color.a/255)*blendFactor`; per channel
`a*color + (1-a)*old`, truncating; result alpha opaque. Modes are passed per
call (rejected: olc's global `nPixelMode`/`fBlendFactor` state, #1595). The
draw seam bounds-checks before blending (recorded deviation: `main` throws for
Alpha/Custom out of range). One file, everything else private (owner).

## Steps

### 0. `Sprite.byteBuffer` widening — check `Pixel.kt`/`Sprite.kt` state, then C5 delta
Tiny additive edit (recorded in the touch-point). KDoc owns-the-bytes warning;
fail-fast after close preserved. Verify existing C5 tests stay green.
- **Decided:** done 2026-09-08 — public `@KGESensitiveAPI` getter over
  `buffer.resource`; new SpriteTest pins live raw storage + fail-fast on the
  accessor after close.

### 1. Mode + blend
- **Compare:** olc `Draw` ALPHA branch (2733–2762) + Pixel ctor (result alpha)
  + the global `fBlendFactor`; `main` `image/Pixel.kt` (Mode) and the blend in
  the old DrawService. Questions: exact rounding/order; result alpha = 255;
  `Alpha.blendFactor` clamp site (constructor `require` vs factory).
- **Tests (hypotheses to confirm):** Normal writes verbatim incl. transparent;
  Mask drops a<255; `Alpha(0.5)` opaque RED over WHITE → `(255,127,127)`;
  result alpha 255; clamp `Alpha(2f)==Alpha(1f)`, `Alpha(-1f)==Alpha(0f)`;
  Custom sees `(x,y,color,old)`.
- **Decided:** compare closed — olc v2.30 `Draw` ALPHA (2733–2762) and `main`
  `DrawService` share the same formula (`a = p.a/255 * f`, truncating casts,
  result opaque — olc's alpha byte is commented out). Adopted `main`'s proven
  shape verbatim (sealed `Mode` nested in `Pixel`; `@JvmInline Alpha` with the
  clamp in a secondary constructor incl. the platform-clash param). No
  deviation. `ModeTest.kt` green on jvm/js/wasmJs (node+browser).
- **Decided:** _fill when the step closes._

### 2. Funnel `draw` + OOB policy
- **Compare:** olc `Draw` (bounds delegated to target buffer, no clip — OOB is
  UB in olc) vs `main` `rasterizer/service/DrawService.kt` OOB per mode
  (the recorded `main` throw). Decide: Normal/Mask no-op + `false`; Alpha/Custom
  no-op (no throw) OOB; in-bounds `true`; and whether point `draw` stays public
  or is internal to the draw seam (lean: primitives public, point internal unless
  a compare/consumer argues otherwise — pin whatever is chosen).
- **Tests:** per-mode in/out of bounds; Boolean observable.
- **Decided:** merged with step 1 (single draw seam unit; OOB composes from the
  C5 accessors). `draw` is public — the draw seam is API. The draw seam
  bounds-checks
  before reading/writing (`x in 0 until w`, `y in 0 until h`), reads the old
  pixel with `uncheckedGet` (never the sample-mode `get`, so a PERIODIC/CLAMP
  target can not wrap an out-of-bounds draw), writes with `uncheckedSet`.
  Normal/Mask delegate to `set` (returns its Boolean); Alpha/Custom no-op
  `false` OOB and never invoke the mode. Recorded deviation (from `main`'s
  Alpha/Custom OOB throw) is materialized. `RasterizerServiceTest.kt` green on
  all targets.

### 3. `fillRect`
- **Compare:** olc `FillRect` (3019–3038: top-left + w,h, loop semantics) vs
  `main` `rasterizer/service/FillRectService.kt`. Our API is an inclusive
  diagonal `(x0,y0)-(x1,y1)`: decide the normalization/clip rule and the
  inverted-diagonal behavior (expected: normalize to min/max → same cells).
  Fast path: NORMAL + whole rows in-bounds on a `Sprite` target → row bulk via
  the existing buffer ops; the observable effect is behavior, and an allocator-
  counting service may assert "no slower".
- **Tests:** inclusive both axes; inverted diagonal; full/partial clip; fully
  outside = nothing; Mask/Alpha per mode.
- **Decided:** compare closed — olc `FillRect` (3019–3038, half-open `[x,x+w)`)
  and `main` `FillRectService` (inclusive rows via `drawSpan`) coincide once
  the inclusive diagonal `(x1-x0+1) x (y1-y0+1)` maps to `w`. Endpoints are
  normalized (either corner order paints the same box), the box is clipped to
  the target, and a rectangle sharing no pixel paints nothing. Fast path on a
  `Sprite` under `Normal` fills each clipped row with `fillInts` (main's
  `drawSpan` precedent — NORMAL ignores alpha, so the raw int equals the
  verbatim draw seam write); every other mode resolves per pixel through the
  active [draw]. The planned allocation-counting speed assertion was dropped
  — the fast path allocates nothing and neither does the draw seam, so no
  observable difference; behavior parity is pinned by a differential test
  (fast path == per-pixel draw seam, alpha included). `RasterizerServiceTest`
  green on all targets.

### 4. `drawLine`
- **Compare:** olc `DrawLine` (2773–2857 — NOT textbook Bresenham: gurkanctn
  variant, `ClipLineToDrawTarget` first, tie asymmetry `px<0` vs `py<=0`) vs
  `main` `rasterizer/utils/BresenhamLine.kt` + `DrawLineService.kt`. Decide the
  algorithm + tie rule (cells can differ on steep/octant ties), inclusivity
  (both ends), and clip-vs-delegate OOB policy. Expected main-parity hypothesis
  `(0,0)→(3,1)` = (0,0)(1,0)(2,1)(3,1) — verified equal in olc already; the
  decision is about the cases where they differ.
- **Tests:** discrete octant cases incl. ties; H/V/diagonal/single point; both
  ends inclusive; neighbors blank; out-of-range segments.
- **Decided:** compare closed — the reference walk is adopted verbatim (olc
  v2.30 `DrawLine`, 2773–2857; tie phase `px<0` in the shallow octant,
  `py<=0` in the steep octant) over `main`'s mirrored Bresenham (`d<=0` → E).
  On exact-midpoint ties the cells differ: `(0,0)->(4,2)` is olc
  `(0,0)(1,1)(2,1)(3,2)(4,2)` vs main `(0,0)(1,0)(2,1)(3,1)(4,2)`. Deviation
  from `main` recorded (olc is the mandate). No clip yet (R2 owns
  clip/viewport): the draw seam drops out-of-bounds cells of the full walk.
  **REVISIT at R2 (pending, do not lose):** for a line with an out-of-bounds
  endpoint the visible cells are the unclipped walk's subset, which can
  differ from the reference's clipped re-walk — the R2 clip math must restore
  exact parity. To be re-flagged at the R2 touch-point.

### 5. `drawRect` / `drawCircle` / `fillCircle`
- **Compare:** olc `DrawRect` (2948–2953, 4 `DrawLine` corner walks — semantics
  for w/h 0 and negative), `DrawCircle` (2863–2898), `FillCircle`
  (2904–2942) vs `main` `DrawRectService`/`DrawCircleService`/
  `FillCircleService` + `utils/BresenhamCircle.kt`. **Decide the drawRect
  diagonal rule here** (olc is start-corner w/h; our API is two diagonal
  endpoints — the cell set of the 4 corner lines is symmetric, but the
  corners/double-writes and w/h=0 semantics must be pinned). Circle: exact
  cells via `dx²+dy²≤r²` (fill) / ring (draw) hypotheses vs the main/olc
  implementations; r=0 = center point; negative r / fully outside = nothing.
- **Tests:** outline sets incl. corners; double-write idempotent under Normal;
  r=2 exact cells; degenerate radii; clip.
- **Decided:** compare closed — `drawRect` = ring of the normalized inclusive
  box via four [drawLine] calls (olc `DrawRect` 2948–2953 draws the same ring
  for any w/h because its four `DrawLine`s are inclusive; corners are written
  twice — idempotent under Normal, double-blended under Alpha, matching the
  reference). Circles: adopted the reference midpoint verbatim (olc
  `DrawCircle`/`FillCircle` 2863–2942, IanM-Matrix1 PR121) — the raster cells
  reach beyond the exact radius (e.g. the r=2 ring includes cells at
  distance √5); the micro-plan's geometric `dx²+dy²≤r²` hypothesis was wrong
  and is replaced by the pinned cells. `main`'s octant-based circle port has a
  `d==0` tie difference from the reference; not adopted (olc mandate). Circle
  guard `circleTouchesTarget` mirrors the reference's fully-outside early
  return; partial-OOB circles drop their out-of-bounds cells (visible
  subset identical to the reference, no re-walk phase — no R2 debt). Same-mode
  per-pixel draw seam through the active [draw]. Tests pin the exact r=2 ring
  (12 cells) and fill rows (21 cells) plus degenerate/negative radius, Mask
  routing, corner double-blend under Alpha, and a partial-OOB fill.

### 6. `fillTriangle` — half-open tie rule
- **Compare:** olc `FillTriangle` (3057–…, scanline interpolation, inclusive
  rows) and `main` `FillTriangleService.kt` (Bresenham walkers + flat-top/bottom
  split) are **both inclusive**; our half-open top/left rule (touch-point)
  deviates from both by decision. Oracle = an independent half-open scanline
  written in the test file, **not** a copy of either reference. Confirm the
  `SortedTriangleVertices` y-then-x sort and collinear→line behavior against
  `main`.
- **Tests:** known rows pinned exactly (e.g. `(0,0)(0,2)(3,2)` → y0: x==0; y1:
  x in 0..1; y2: x in 0..2); ~300 seeded random triangles vs the oracle cell-
  by-cell; ~100 blended differential over a base; degenerate/collinear → Filled
  line of extremes; adjacent triangles share an edge with no double-blend.
- **Decided:** compare closed — olc `FillTriangle` (scanline interpolation,
  inclusive rows, paints the base-vertex row) and `main` `FillTriangleService`
  (Bresenham walkers, flat-top/bottom) both paint the base-vertex row and both
  double-write a shared edge under Alpha; both rejected (the touch-point half-
  open rule is the mandate — double deviation recorded). The rasterizer samples
  pixel centers: a pixel is filled iff the center `(x+0.5, y+0.5)` is inside the
  winding-normalized triangle, top/left edges open, bottom/right edges closed
  (GPU top-left). Engine path is integer: edge functions over the doubled
  center `(2x+1, 2y+1)`; winding normalized by swapping b↔c when the signed
  area is negative; an edge is open iff `dy<0 || (dy==0 && dx>0)` following the
  topmost-vertex traversal. Zero-area (collinear) triangles draw the single
  line between the farthest vertex pair via the active [drawLine]. Consequence
  of the center rule: **the base-vertex row is never painted** (its centers lie
  beyond the vertex); `(0,0)(0,2)(3,2)` paints exactly `{(0,0),(0,1),(1,1)}`
  (the micro-plan's `y2` hypothesis was wrong). The independent oracle is a
  float-center half-open scanline living in the test file; 300 seeded random
  triangles differential cell-by-cell (100 over a base under Alpha) plus the
  adjacency test (two triangles sharing a diagonal paint the four square cells
  once each, seam single-blend `(255,127,127)`, never the double `(255,63,63)`)
  green on jvm+js+wasmJs.

### 7. `drawTriangle`
- **Compare:** olc `DrawTriangle` (3044–3050: three `DrawLine`s) vs `main`
  `DrawTriangleService.kt`. Decide outline inclusivity and shared-corner
  double-writes (idempotent under Normal) plus the collinear/degenerate
  handling (expected: single line p0..p2 of the sorted extremes).
- **Tests:** outline cells; collinear → single line; degenerate.
- **Decided:** compare closed — olc `DrawTriangle` (3044–3050) is three
  inclusive `DrawLine`s in input order; `main` sorts the vertices
  (`SortedTriangleVertices`, y-then-x) and special-cases collinear as a single
  draw between the sorted extremes. Both references double-write the shared
  corners (each vertex is the endpoint of two edges). The outline is three
  [drawLine] calls over the active facade in vertex order — the painted set is
  order-independent, so no sort is needed; collinear vertices draw once
  between the farthest pair (shared `drawFarthestPairLine` helper with the
  `fillTriangle` collinear branch) so a collinear triangle under Alpha blends
  each cell exactly once instead of twice/three times. A degenerate triangle
  (two equal vertices) is collinear and draws the remaining distinct line.
  Tests pin: outline == the three-line composition, order invariance, corner
  double-blend under Alpha (single vs double cells enumerated), collinear
  single-blend, degenerate, fully outside. Green on jvm+js+wasmJs.

### 8. `drawSprite` + `Flip` + scale
- **Compare:** olc `DrawSprite` (3359–3430: per-scaled-cell loop, `flip`
  uint8 bits H=1/V=2) vs `main` `DrawSpriteService.kt`. Decide integer-scale
  block mapping, flip application order, and source-alpha via Mask/Alpha.
  Fast path `scale==1 && Normal` vs `scale>1` block path — behavioral parity
  pinned, structure not asserted.
- **Tests:** full/partial blit; each Flip pins flipped positions; OOB reject;
  sprite alpha via Mask/Alpha.
- **Decided:** compare closed — olc `DrawSprite` (3359–3430) blits source
  column `i` to dest `x + i*scale` and reads flipped axes by walking the read
  index in reverse (`fxs = w-1; fxm = -1`), so a flip mirrors the source inside
  the same top-left-anchored footprint; `main` mirrors the same mapping and
  additionally rejects `scale <= 0` (olc's unsigned scale makes 0 a silent 1:1).
  Adopted: `Sprite.Flip` nests in `Sprite` as `enum NONE/HORIZONTAL/VERTICAL/
  BOTH` (`main`'s shape, per the touch-point); `scale <= 0` paints nothing
  (main's policy); the whole footprint fully outside the target paints
  nothing; a source pixel at `(sx, sy)` becomes a `scale x scale` block at
  `(x + sx*scale, y + sy*scale)`. Composition: every destination pixel resolves
  per pixel through the active [draw] so Mask drops non-opaque source pixels and Alpha
  blends them (mode applies to the sprite's own color, as in both references).
  Fast path (behavior pinned by a random-content differential, structure not):
  `scale == 1 && Normal && no horizontal flip && whole blit in bounds` on a
  `Sprite` target copies whole rows with `copyInts` (the vertical flip only
  swaps which source row lands where; horizontal reversal excludes the fast
  path). Tests pin: 1:1 blit positions, each `Flip` mirrored cells (H/V/BOTH
  per cell), scale-2 block painting, random-content parity for NONE/VERTICAL,
  partial-blit clipping + fully outside, Mask/Alpha on source alpha,
  `scale <= 0` no-op. Green on jvm+js+wasmJs.

### 9. Seam extension-contract
- Decorator overriding `fillRect` changes the observable result while
  `drawLine` delegates to `original`; `resetAll` restores. Same shape as prior
  T2 extension-contract tests.
- **Decided:** done 2026-09-08 — the proof lives in `RasterizerServiceTest`
  (a local `delegateTo(original)` factory + Kotlin `by` delegation so a test
  decorator overrides a single method). Pinned: a decorator overriding only
  `fillRect` is observable by the facade fill and untouched methods delegate
  to the engine default; `drawTriangle` resolves its three edges through an
  active `drawLine` override (composites resolve sub-draws through the active
  facade, never the original); `KGEOverridable.Proxy.resetAll()` restores the
  default. Green on jvm+js+wasmJs.

### 10. Native bulk `fillInts`/`copyInts` (expect/actual)
No olc counterpart (log #31 materialized; benchmark spike recorded at the
touch-point). Common `internal` loop fns + `expect`; JVM actual (IntBuffer bulk
copy; fill keeps the loop — no measured gain), web actual (`Int32Array`
`fill`/`set` over the same backing buffer; non-KGE source falls back to the
loop). Tests: parity/round-trip vs the loop on a region; overlap memmove-safe;
existing `ByteBufferTest` cases unchanged.
- **Decided:** done 2026-09-08 — shape: the public `fillInts`/`copyInts`
  extensions keep the bounds contract (`requireRange`) and their memmove loop,
  and dispatch first to `internal expect` hooks `nativeFillInts`/
  `nativeCopyInts` returning whether the platform performed the bulk op.
  JVM actual: fill declines (java.nio has no int-pattern fill; the putInt loop
  already runs at bulk speed), copy transfers an `IntBuffer` view of a
  `duplicate()` over the region (`asIntBuffer().put(...)`, the measured
  ~4-7x); same-buffer copies, unaligned offsets and read-only targets decline
  to the common loop, which owns the memmove. Web actual: `nativeIntView` on
  the web `ByteBuffer` exposes the backing as an `Int32Array`; copy routes
  through `set(subarray, offset)` (memmove-safe by spec), fill through a
  doubling fill — **the wrapper exposes no typed-array `fill`**, so the first
  int is written via `putInt` and each further int is replicated by `set`s of
  the filled prefix. Two findings pinned by tests: the doubling source length
  must be the copied `block`, not the whole `filled` prefix (a non-power-of-two
  region overflowed — regression test fills a whole 36-byte region), and
  whole-region lengths must cover any count. ByteBufferTest overlaps (both
  directions) and cross-buffer copy exercise the native paths on every target;
  all prior cases green unchanged. Green on jvm+js+wasmJs.

### 11. Gate + close
`./gradlew build --rerun-tasks` green; leak audit (no allocations beyond the
caller's surface) + no-parameter-without-observable-effect audit; two-axis
review; decisions-log entry (this rev, the per-step `Decided:` outcomes, the
byteBuffer widening, any main/olc deviations).

## Redesign (owner review, 2026-09-08 — supersedes the single-file shape above)

The concept-close review (Hunk notes) rejected the giant single seam file
(`RasterizerService.kt`, ~760 lines) and asked for `main`-style decomposition.
Decided with the owner:

- **Split by scope into sub-services, each an overridable seam, aggregated by
  one public entry point.**
  Files (package `dev.staticsanches.kge.rasterizer.service`, matching `main`'s
  layout): `DrawService` (the per-pixel mode-resolving write `draw` + the
  blend math + OOB policy — the concept named "funnel" in the earlier
  narrative, now **the draw seam**; the old term is retired from code and
  docs), `OutlineService` (`drawLine`/`drawRect`/
  `drawCircle`/`drawTriangle`), `FillService` (`fillRect`/`fillCircle`/
  `fillTriangle`), `DrawSpriteService` (`drawSprite`) — each `: KGEOverridable`
  with its own `Proxy` and per-family default. `Rasterizer` (package
  `dev.staticsanches.kge.rasterizer`) is the single call site: a `data
  object` delegating `by` each sub-service companion (`main`'s `Rasterizer`
  shape). Composite primitives resolve their sub-draws through the *active*
  sub-service (`FillService`/`OutlineService`/`DrawSpriteService` defaults
  call `DrawService.draw` per pixel, `OutlineService` composites call
  `OutlineService.drawLine`), so a decorator override of any sub-service is
  observed by the composites that consume it — the step-9 extension-contract
  proof moves to the sub-services (`delegateTo` factories per family; a test
  pins that an active `DrawService` override is observed by a composite
  fill).
- **Keep the `Int`-coordinate API; no `Int2D` yet.** This kernel has no point
  type and no planned math/vector concept. **Roadmap note (do not lose):**
  vectors/points are the next concept to treat; the sub-services created here
  (drawLine/drawTriangle/fillTriangle and the raster signatures in general)
  are the first consumers to adjust when a point type lands.
- **Native bulk `fillInts`/`copyInts` merge into a single `BufferService`
  (owner review)** — `MemoryAllocatorService` is renamed `BufferService`
  (`kge-core/.../buffer/BufferService.kt`) and owns allocation *and* the bulk
  operations as **`void` methods whose default bodies here are the portable
  fallback** (memmove-safe loop); platform implementations override only when
  they have a native path and call `super` conditionally (JVM: copy via
  `IntBuffer` bulk, fill inherits the loop — java.nio has no int fill; web:
  typed-array fill/copy over the backing view). The companion validates the
  range first (as it does for negative sizes), so implementations only see
  legal regions. Platform files renamed `BufferServiceJvm`/`BufferServiceWeb`
  (`LwjglBufferService`/`WebBufferService`). The whole service — allocation
  and bulk — is runtime-substitutable and testable.

## Files

New: `image/RasterizerService.kt` superseded by `rasterizer/Rasterizer.kt`
(the `data object` aggregate) + `rasterizer/service/DrawService.kt` +
`rasterizer/service/OutlineService.kt` + `rasterizer/service/FillService.kt`
+ `rasterizer/service/DrawSpriteService.kt` (the four per-scope
`KGEOverridable` seams; the "everything in one file, all helpers private"
shape above is superseded by the Redesign section); `buffer/BufferService.kt`
(allocator + bulk strategy seam, jvm/web defaults in `BufferServiceJvm`/
`BufferServiceWeb`).
`commonTest`: `ModeTest.kt`, `RasterizerServiceTest.kt` (all primitive +
extension-contract tests; the planned `TriangleTieRuleTest.kt`/
`SpriteFlipTest.kt` files were folded here). Additive: `Pixel.kt` (nested
`Mode`), `Sprite.kt` (nested `Flip` + `byteBuffer` widening),
`buffer/ByteBuffer.kt` (fills/copies route through `BufferService`).

Out of scope (touch-point): `LinePattern`, decals/text (R3/R6), viewport/clip
(R2), octant circle masks.
