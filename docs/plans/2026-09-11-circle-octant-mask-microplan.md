# Circle octant masks micro-plan (interactive execution)

**Date:** 2026-09-11. Touch-point decisions in
`docs/decisions/phase-1/17-circle-octant-mask.md` (this session). Concept: add a
`CircleOctantMask` to `OutlineService.drawCircle` and `FillService.fillCircle`
(raw + `Int2D`, forwarded by `Rasterizer`); `ALL` = today's full circle.

## Working mode

- **No subagents.** One step = compare → decide → test (red) → implement (green
  on jvm+js+wasmJs) → mark. Doubts resolved in-session with the owner.
- **Oracle = olc v2.30 + `main` + an independent float oracle.** olc v2.30
  masks the outline only (`DrawCircle(..., mask = 0xFF)`); `FillCircle` has no
  mask. `main`'s mask-aware `fillCircle` is broken (exclusive `j in x..<y` drops
  the outer spoke; `ALL` is not a disc) and double-writes — it is **not** an
  oracle (touch-point finding). `ALL` parity is pinned against the current C6
  behavior; the wedge shapes get an independent `atan2` oracle in the test file
  (the `fillTriangle` float-oracle precedent).
- **Sources:** olc v2.30 = `.tmp/olc/olcPixelGameEngine.h` (`DrawCircle`
  2863–2898, `FillCircle` 2904–2942). `main` = `git show main:<path>`.
- **`ALL` is the hard invariant.** The masked path must leave `drawCircle`/
  `fillCircle` `ALL` pixel-identical **and write-count-identical** to C6 under
  every mode (`Alpha`/`Custom` included): the fill keeps the row-span, the
  outline keeps its loop; only the mask gating is added.

## Resume tracker (a new session starts at the first unchecked step)

- [x] **0. `CircleOctantMask` type** (`rasterizer` package; O1..O8, ALL, NONE, or, intersects)
- [x] **1. `drawCircle` mask** (outline gating; raw + `Int2D` + `Rasterizer` forward)
- [x] **2. `fillCircle` mask** (row-span octant gating; raw + `Int2D` + forward)
- [x] **3. Decorator/contract + gate + decisions-log close**

## Contract (touch-point)

- `CircleOctantMask` is a `@JvmInline value class` with a private `Int`, in
  `dev.staticsanches.kge.rasterizer`. Constants `O1..O8`, `ALL`, `NONE`;
  `infix fun or(other): CircleOctantMask`, `infix fun intersects(other): Boolean`.
  Orientation: `O1` top→NE clockwise, `O2` NE→E, `O3` E→SE, `O4` SE→S,
  `O5` S→SW, `O6` SW→W, `O7` W→NW, `O8` NW→N; `O1 = 0b1000_0000` (MSB) …
  `O8 = 0b0000_0001` (LSB); `ALL = 0xFF`, `NONE = 0x00`.
- **Boundary ownership = odd octants.** A cell exactly on an axis or diagonal
  belongs to `O1`/`O3`/`O5`/`O7` (outline `x == 0`/`x == y` gating; `main`'s
  convention). Center cell painted iff mask != `NONE`.
- Signatures (mask required, after `radius`, before `color`):
  `drawCircle(target, cx, cy, radius, mask, color, mode)`,
  `drawCircle(target, center: Int2D, radius, mask, color, mode)`, and the same
  for `fillCircle`.
- **`mask == NONE` paints nothing, including `radius == 0`.** `radius == 0`
  with a non-`NONE` mask paints the center; negative radius nothing.
- The masked-fill mechanism: keep the row-span; for `mask == ALL` call the
  current `fillRow` unchanged (exact parity, one write per cell). Otherwise split
  each drawn row's span at `dx = -|dy|, 0, +|dy|` (clamped to the span) and paint
  each segment whose octant is set. `dy = y - cy`; rows at `cy ± x` have
  `|dy| = x`, rows at `cy ± y` have `|dy| = y`, the `x == 0` row has `dy = 0`
  (segments `O7`/center/`O3`). The classifier is integer `|dx|` vs `|dy|`; the
  engine's classification was validated against an `atan2` oracle on all
  interior disc cells (radii 2..19, no mismatch) and is a proper partition.

## Steps

### 0. `CircleOctantMask` type

- **Tests (red first):** `ALL intersects` every single; `NONE intersects` none;
  `O1 or O2` intersects exactly `O1`/`O2`/`O1 or O2`; `NONE or O3 == O3`;
  `ALL == O1 or … or O8`. (No raw-int test — the bit layout is internal; the
  orientation is pinned behaviorally in steps 1–2.)
- **Files:** new `rasterizer/CircleOctantMask.kt`.
- **Decided:** done 2026-09-11 — `@JvmInline value class` in `rasterizer`, private `Int`,
  `O1..O8`/`ALL`/`NONE` + `infix or`/`infix intersects` (no raw-bit factory). Set
  algebra pinned in `CircleOctantMaskTest` (ALL intersects every single, NONE none,
  `O1 or O2` exactly the union, `NONE or O3 == O3`, `ALL` = the full union); the bit
  layout stays internal and the orientation is pinned behaviourally in steps 1–2.

### 1. `drawCircle` mask

- **Compare:** olc `DrawCircle` (2863–2898: `mask & 0x01/0x04/0x10/0x40` for the
  unconditional points, `0x02/0x08/0x20/0x80` under `x0 != 0 && x0 != y0`) vs the
  current loop. The current loop already maps the same 8 points with the same
  guard, so gating each `DrawService.draw` by its octant is exact for `ALL`.
  Confirm the point↔octant map: `(x,-y)=O1`, `(y,-x)=O2`, `(y,x)=O3`, `(x,y)=O4`,
  `(-x,y)=O5`, `(-y,x)=O6`, `(-y,-x)=O7`, `(-x,-y)=O8`.
- **Tests:** `ALL` r=2 == the existing `ringR2` (param added); each single octant
  paints exactly the ring cells whose `atan2` wedge is that octant (odd owns
  boundaries — the axes/diagonals land in O1/O3/O5/O7); a two-octant `or`
  (e.g. `O1 or O2` = the NE quadrant); `NONE` r=2 == empty; `NONE` r=0 ==
  empty; r=0 non-`NONE` == center; negative radius == empty. `Int2D` overload
  forwards the mask; `Rasterizer` forwards it.
- **Update call sites:** `RasterizerTest.kt` (388, 395, 399, 406),
  `RasterizerPointOverloadTest.kt` (73, 74, 273–280 decorator, 317).
- **Decided:** done 2026-09-11 — each `DrawService.draw` of the midpoint loop is gated by
  its octant's bit; the existing `x == 0` / `x == y` guard is unchanged, so axes and
  diagonals stay with the odd octants (and the even points that coincide with them are not
  double-drawn). Tests pin `ALL` r=2 against the existing `ringR2`, per-octant rings against
  the `atan2` oracle, the `O1 or O2` NE quadrant, NONE/radius-0/negative precedence, the
  odd-owned diagonal at r=3 (the first arc that reaches `x == y`), an outline crossing the
  edge (visible arc subset), and masked forwarding through the `Int2D` overload and the
  `Rasterizer` aggregate against the raw service.

### 2. `fillCircle` mask

- **Compare:** olc `FillCircle` (2904–2942, no mask) vs the current row-span.
  The mask lives entirely in the new gating; `main`'s spoke is not used.
- **Tests:** `ALL` r=2 == the existing `fillR2`; each single octant fills the
  disc cells whose `atan2` wedge is that octant (independent oracle over the
  whole disc, boundary ownership odd, center in any non-`NONE` mask); a quadrant
  `or`; `NONE` == empty (r=2 and r=0); r=0 non-`NONE` == center; negative radius
  == empty; **`ALL` under `Alpha`/`Mask` equals a per-cell `DrawService` reference
  over the same disc** (single write per cell — no double blend); partial-OOB
  masked fill paints the visible subset. `Int2D`/`Rasterizer` forward.
- **Update call sites:** `RasterizerTest.kt` (423, 430, 434, 441),
  `RasterizerPointOverloadTest.kt` (132, 133, 142, 143, 148).
- **Decided:** done 2026-09-11 — the C6 row-span is kept and each drawn row is split at
  `cx±|dy|` (plus `cx`), painting only the selected octant segments; on a boundary the odd
  octant owns the cell, the `dy == 0` row paints the center for any non-NONE mask, and
  `mask == ALL` short-circuits to the unchanged `fillRow` (exact parity, one write per cell).
  Tests pin `ALL` r=2 and an independently derived r=5 row-span disc, per-octant/quadrant
  disc against the `atan2` oracle, the `ALL` set under Alpha/Mask against a one-draw-per-cell
  `DrawService` reference over the independent disc, masked partial-OOB and fully-OOB fills,
  NONE/r=0/negative precedence, and masked forwarding through `Int2D`/`Rasterizer`.
  Review-fix pass: the dead `mask != NONE` guard in the `dy == 0` branch is removed (the
  caller already returns on NONE) and the redundant private-helper KDoc on `fillOctantRow`
  is dropped to match the file's private-helper convention (`fillCircleRow` keeps its KDoc,
  the split being non-obvious).

### 3. Decorator/contract + gate + close

- A decorator overriding `drawCircle`/`fillCircle` with the mask signature is
  observed by `Rasterizer` (extends the existing point-overload decorator
  test). KDoc: the octant diagram + the boundary/center rules on both methods
  and the type.
- **Gate:** `./gradlew build --rerun-tasks` green (all targets + ktlint +
  metadata/assemble). Leak audit (no allocations) + no-parameter-without-
  observable-effect audit. Two-axis review. Decisions-log close entry (this
  plan, the per-step `Decided:`, the `main`/olc deviations already recorded at
  the touch-point).
- **Decided:** done 2026-09-11 — by-wrapped decorators overriding the masked raw
  `drawCircle`/`fillCircle` are observed by the `Rasterizer` aggregate through the raw
  entry (the `by` delegation forwards the typed entry to `original`, so a raw override is
  exercised via the raw call) and `resetAll()` restores the default; the KDoc octant diagram
  and boundary/center rules live on the type and the public masked methods. The two-axis
  review's findings were integrated: the redundant NONE guard removed, non-`ALL` forwarding
  and decorator contracts added, the masked partial-OOB test made non-vacuous, diagonal
  ownership pinned at r=3, the `ALL` single-write proof re-founded on an independent r=5
  disc, private-helper KDoc aligned, and this bookkeeping. Gate green. The decisions-log
  close entry is the orchestrator's next step.

## Files

New: `rasterizer/CircleOctantMask.kt`. Modified:
`rasterizer/service/OutlineService.kt`, `rasterizer/service/FillService.kt`,
`Rasterizer.kt` (nothing — the aggregate delegates by type), `commonTest`
`RasterizerTest.kt`, `RasterizerPointOverloadTest.kt`, plus a new
`CircleOctantMaskTest.kt`.

Out of scope: rect/triangle primitives; any addon API (C10 owns the `ALL`
default); `SpritePatch`/`DecalPatch`.
