## 2026-09-11 — Circle octant masks: touch-point decisions

Post-R2 raster widening (roadmap "2026-09-10 post-R2 additions", item 1; the
last of the three post-R2 sessions). Macro requirement: `OutlineService.drawCircle`
and `FillService.fillCircle` (raw + `Int2D`, forwarded by `Rasterizer`) gain a
`CircleOctantMask` selecting which of the eight octants are painted. No change to
rectangles or triangles.

### Verified facts (olc v2.30, main, current code)

- **olc masks the outline only.** olc v2.30 `DrawCircle(x, y, radius, p, mask =
  0xFF)` has an 8-bit octant mask; `FillCircle(x, y, radius, p)` has **no** mask
  (plain row-span). `main` added its typed `CircleOctantMask` to **both** — the
  owner wants both, which matches the roadmap entry.
- **The outline mask is a pure extension of the current code.** The current
  `OutlineService.drawCircle` midpoint loop is pixel-identical to `main`'s masked
  outline with `ALL` (`mask = 0xFF`); simulated and confirmed for radii 1..15.
  The only change is gating the existing eight `DrawService.draw` calls by the
  mask bit of their octant.
- **`main`'s mask-aware `fillCircle` is broken and is not an oracle.** It uses
  `bresenhamCircle(radius, mask) { ... for (j in x..<y) { ... } }` — the
  exclusive `..<y` bound drops the outer pixel of every spoke, so even with
  `ALL` it is not a full disc: radius 1 paints the center only (1 cell vs olc's
  5), radius 5 paints 69 cells vs olc's 97. It cannot be ported as a parity
  reference.
- **A corrected spoke (`j in x..y`, inclusive) matches olc's cell *set* for
  `ALL` in radii 1..40, but double-writes cells** — the axes and diagonals are
  emitted twice (the `x == 0` and `j == x` collapses in the spoke). olc's
  row-span writes each cell exactly once, so the spoke would change
  `Alpha`/`Custom` results versus C6 (the blend reads the stored pixel). So the
  correct fill mask must keep the C6 row-span algorithm and select octants
  inside it.
- **The current `fillCircle` is the olc v2.30 row-span disc** (radius 0 paints
  the center, negative radius nothing, `circleTouchesTarget` short-circuits OOB).
  Its C6 parity oracle is olc, under `Normal`/`Mask`.
- **No raster service parameter has a default value.** `target`/`color`/`mode`
  and every other service parameter is required; the `main` defaults
  (`color = Colors.WHITE`, `mask = ALL`) live in the addon layer, which does not
  exist here yet (arrives at C10/E2).

### Decisions (owner)

- **Type: `CircleOctantMask` in package `rasterizer`** (beside `Rasterizer`/
  `Viewport`), not `math` — it is a raster concept with only circle
  rasterization as consumer.
- **Orientation follows `main`, clockwise from the top.** `O1` = top→NE,
  `O2` = NE→E, `O3` = E→SE, `O4` = SE→S, `O5` = S→SW, `O6` = SW→W,
  `O7` = W→NW, `O8` = NW→N; bit layout `O1 = 0b1000_0000` (MSB) …
  `O8 = 0b0000_0001` (LSB); `ALL = 0xFF`, `NONE = 0x00`. (Note this renumbers
  olc's raw bits — olc `0x01` is the same point as our `O1` — so the type's named
  constants, not raw olc bit values, are the API.)
- **Boundary ownership is the odd octants.** A pixel exactly on an octant
  boundary (the four axes and four diagonals) belongs to `O1`/`O3`/`O5`/`O7`,
  matching the outline's `x == 0` / `x == y` gating. The center pixel is painted
  whenever the mask is not `NONE` (every selected wedge has the center as apex),
  matching `main`'s spoke at `j == 0`.
- **Public surface:** `O1..O8`, `ALL`, `NONE`, `infix fun or`, `infix fun
  intersects` (`and`/`xor` and the binary `toString` are dropped — no consumer,
  YAGNI). Private constructor (`@JvmInline value class` over an `Int`); no raw
  bit factory.
- **Fill strategy: keep the C6 row-span and gate octants inside it**, so `ALL`
  stays pixel-identical **and write-count-identical** to today under every mode
  (`Alpha`/`Custom` included). The masked result is the olc disc intersected
  with the selected wedges. Porting `main`'s spoke algorithm is rejected (broken
  `ALL` + double writes).
- **Signature and order.** The mask sits right after `radius`, before `color`
  (olc/`main` placement): raw `(target, cx, cy, radius, mask, color, mode)`,
  `Int2D` `(target, center, radius, mask, color, mode)`. `Rasterizer` forwards
  it. No default value on the service seam (repo pattern); `ALL` defaults belong
  to the addon at C10/E2.
- **`mask == NONE` paints nothing, including `radius == 0`** (follows `main`;
  `NONE` explicitly means "no octant"). `radius == 0` with a non-`NONE` mask
  still paints the center; a negative radius paints nothing.

### Open for the micro-plan (not answerable at the touch-point)

- The exact masked-fill mechanism (segment split versus per-cell octant
  classification) and the precise half-open segment bounds that realise the
  odd-owns-boundary rule while keeping `ALL` exact.
- Whether the shared midpoint-arc step generator is factored into an internal
  helper or kept inline in each primitive.
- KDoc diagram for the octant orientation; the differential/per-octant test
  inventory (independent oracle for the wedge shapes; `ALL` re-pinned against
  the existing C6 cases; `NONE`/`radius 0` precedence; the `Int2D` overloads and
  the `Rasterizer` decorator forward the mask).

Micro-plan next: `docs/plans/2026-09-11-circle-octant-mask-microplan.md`, TDD per
step, then the usual gate/review.

## 2026-09-11 — Circle octant masks: closed

Micro-plan delivered. Final shape:

- **`CircleOctantMask`** (`rasterizer`): `@JvmInline value class` over a private
  `Int`; `O1 = 0b1000_0000` (top→NE) … `O8 = 0b0000_0001` (NW→N) clockwise,
  `ALL = 0xFF`, `NONE = 0x00`; only `or`/`intersects` (no `and`/`xor`/binary
  `toString`, no raw factory; `and`/`xor` had no consumer — YAGNI). This
  renumbers olc's raw bits — olc `0x01` is our `O1` — so the named constants,
  not raw values, are the API. Axes and diagonals belong to the odd octants
  `O1`/`O3`/`O5`/`O7` (matching the outline's `x == 0`/`x == y` gating); the
  center is painted iff the mask is not `NONE`.
- **`mask` is required** on `drawCircle`/`fillCircle` (raw + `Int2D`, forwarded
  by `Rasterizer`), placed after `radius` and before `color`. No default on the
  service seam (repo pattern); the `ALL` default belongs to the addon at
  C10/E2. Semantics: `NONE` paints nothing including `radius == 0`; `radius ==
  0` with a non-`NONE` mask paints the center; a negative radius paints
  nothing.
- **`ALL` is the untouched C6 behavior** — pixel- *and* write-count-identical
  under every mode. The outline keeps its loop (each `DrawService.draw` wrapped
  in an `intersects` guard). The fill keeps the olc row-span, with `ALL`
  short-circuiting to the unchanged full-span `fillRow`; the masked path gates
  each drawn row into octant segments at `dx = -|dy|, 0, +|dy|` — a gap-free
  partition whose union is the `ALL` disc.
- **`main`'s mask-aware `fillCircle` rejected.** Its exclusive `j in x..<y`
  spoke drops the outer pixel, so `ALL` is not a disc (radius 1 paints the
  center only, 1 cell vs olc's 5); the inclusive variant double-writes the axes
  and diagonals, changing `Alpha`/`Custom`. The C6 row-span is the algorithm;
  `main` is not an oracle.
- **Test evidence.** `ALL` re-pinned to the existing C6 sets and to an
  independently hand-derived `r=5` row-span disc (the single-write proof under
  `Alpha`/`Mask` compares against a one-draw-per-cell reference over it);
  per-octant and quadrant shapes against an independent `atan2` oracle (not a
  copy of the engine's integer classifier); the outline diagonal ownership at
  `r=3` (the first arc reaching `x == y`); a non-vacuous masked partial-OOB;
  non-`ALL` forwarding through the `Int2D` overloads and the `Rasterizer`
  aggregate; by-wrapped decorators observed through the raw entry with
  `resetAll()` restoring the default. Independent replays extended the check to
  radii 0..60 × 82 masks with zero fill/outline mismatches and zero double
  writes.
- **Review (two axes, one fix round, no hard findings).** Resolved: the
  non-`ALL` mask forwarding and the decorator contract were pinned; the
  partial-OOB test was made non-vacuous; the outline diagonal ownership was
  added; the `ALL` single-write proof was re-founded on an independent disc;
  the dead `NONE` guard in the fill's `dy == 0` branch was removed and a
  `fillCircleRow` KDoc clause corrected.
- **Gate:** `./gradlew build --rerun-tasks` green (all targets + ktlint +
  metadata/assemble).
