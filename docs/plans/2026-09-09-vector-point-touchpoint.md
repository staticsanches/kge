# Vector/point — touch-point material

**Date:** 2026-09-09. Pre-touch-point material for the vector/point concept —
not yet catalogued in the macro roadmap; recorded there as the next concept to
treat at the C6 close review (2026-09-08, roadmap "Vector/point concept"
entry): no point type exists and the raster sub-services created in C6 are the
named first consumers to adjust. Decisions closed in discussion (owner,
2026-09-09) are recorded here; open items are decided at the touch-point.

## Investigation summary

- **olc v2.30:** one `template<class T> struct v_2d<T>` (C++ compile-time
  duck typing) with aliases `vi2d`/`vu2d`/`vf2d`/`vd2d`; only `vi2d` and
  `vf2d` are exercised by the engine (`vu2d`/`vd2d` are declared, unused).
  Typed overloads at public API forward to raw-coordinate implementations
  (e.g. `DrawLine(vi2d, vi2d)` → `DrawLine(pos.x, pos.y, ...)`); raster loops
  stay raw. Float-domain math (mag/floor/ceil/lerp) appears only in decal/
  layer/CPU-texture code.
- **main (evidence, not mandate):** concrete `data class Int2D`/`Float2D` +
  `MutableInt2D`, thin operator sets. Real production consumers: engine draw
  addons (Int2D positions/boxes, dual overloads), `DimensionState`/Window
  (sizes, `Float2D.oneByOne / Int2D`), surface `size: Int2D`, decal
  `uvScale = oneByOne / sprite.size`, and one `MutableInt2D` cursor in
  `DrawStringService`. Decal vertex math is done on raw components.
- **Current kernel:** no vector type; all coordinates raw (`Int` raster and
  surface access, `Float` sampling). Only in-kernel "missing point" site:
  `OutlineService.drawFarthestPairLine` builds nested `Pair`s
  (`x0 to y0 to (x1 to y1)`). Surface stays raw by C5 decisions.

## Decided (2026-09-09 discussion, owner)

1. **Form — two concrete `data class`es, no generic.** `Int2D(x: Int, y: Int)`
   and `Float2D(x: Float, y: Float)`. Kotlin has no arithmetic generics; a
   generic would need `Number` casting (boxing per op on every target, the
   exact cost that ruled out Long packing) or a self-built typeclass. The
   reference itself only exercises Int and Float. Ecosystem precedent (JOML,
   Compose `IntOffset`/`Offset`) is concrete types, not one generic.
2. **No shared interface.** No polymorphic consumer exists; add an
   `interface Vector2D` only when one appears (YAGNI).
3. **Explicit conversion** between the domains (e.g. `Int2D.toFloat()`),
   covering the role of olc's implicit cast without its accidental-mix
   footguns.
4. **`toString()` = `"($x, $y)"`** (space after the comma) on both.
5. Packing into `Long` rejected: JS has no 64-bit primitive (`Long` is a
   two-field object on the ES5 backend, a `BigInt` otherwise — stdlib
   `boxedLong.kt`/`longAsBigInt.kt`), so packing is worse than two plain
   fields there; and no per-pixel vector consumer exists (unlike `Pixel`),
   so the JVM benefit is moot.

## Open — decided at the touch-point

- **Placement:** as a standalone concept between C6 and C7, with C7 text,
  C8 dimension state and R3 decals (`Float2D`) as the near consumers. Ordering
  to be confirmed against the roadmap.
- **Operator set — minimal, each pinned by a consumer + test.** Candidates
  evidenced so far: componentwise `+ -`; `*`/`/` (vector and scalar, Int and
  Float); `Float2D / Int2D` (main's `invertedScreenSize`); explicit
  conversions. Deferred by rule: mag/norm/perp/cross/floor/lerp/clamp and
  ordered comparison (olc's `<`) — add at the consumer concept that needs
  them, with tests.
- **Convenience surface:** `zeroByZero`/`oneByOne`, `by` factory — keep only
  what a consumer uses (evidence: main's `by` and `oneByOne` were used;
  `zeroByZero` largely as state defaults).
- **`MutableInt2D`:** rejected by default; immutable + local `var` covers the
  text cursor unless C7 proves real mutation. Revisit with a test at C7.
- **Where to type:** dual typed overloads at the *public* surface
  (Rasterizer aggregate, later engine draw API) forwarding to the raw
  coordinate implementations — mirroring olc/main; **not** in the per-pixel
  seam (`DrawService`) or the raster primitive loops. The C6 close note names
  the sub-service signatures as first consumers to adjust; the touch-point
  decides the exact overload set and whether `Rasterizer` gains a point-taking
  form.
- **Surfaces untouched:** `width`/`height` stay raw (C5 decision); no
  `size: Int2D` reintroduction.
- **Package/naming:** `math.vector` + `Int2D`/`Float2D` kept (main convention);
  confirm.

## Framing for the touch-point

Pure math type per roadmap principle 3 (no service seam, no lifecycle). API
discipline: every public member/parameter has an observable effect pinned by a
test; ops without a consumer are YAGNI and land at the consumer concept. The
concept's own micro-plan is TDD, written at the touch-point.
