# Vector/point micro-plan

**Date:** 2026-09-09. Touch-point decisions in
`docs/plans/2026-09-09-vector-point-touchpoint.md`; the open items were closed
with the owner in this session (answers below). Concept: the pure
`Int2D`/`Float2D` types + typed `Int2D` overloads on the raster sub-service
interfaces (default implementations delegating to the existing raw methods),
exposed through the raster aggregate.
Executed step-by-step in-session with the owner: compare → decide → test (red)
→ implement (green on jvm+js+wasmJs) → mark, following the C6 rev 2 working
mode. Per-step expectations below are hypotheses; a finding that contradicts
one overrides it and is recorded in the step's `Decided:` field.

## Decisions (owner, 2026-09-09 — supersedes the touch-point material's open list)

- **Form:** two concrete `data class`es, `Int2D(Int, Int)` / `Float2D(Float,
  Float)`. No generic, no shared interface, no `Long` packing (JS has no
  64-bit primitive — stdlib `boxedLong.kt`/`longAsBigInt.kt`), no
  `MutableInt2D` (no consumer; revisit at C7 only if text proves mutation).
- **Scope of integration:** typed `Int2D` overloads are added to the raster
  sub-service **interfaces** (`OutlineService`, `FillService`,
  `DrawSpriteService`) as **default implementations that delegate to the
  existing raw methods** — each unpacks the point and calls the already-
  declared primitive method on `this`, so a plain implementation object that
  overrides only the raw method gets the typed form for free (its default
  unpacks to its own override). The companion **`Proxy` of each modified
  service mirrors every typed overload with an explicit override that forwards
  the *analog* method to `delegate`** (`delegate.drawLine(target, start, end,
  color, mode)`) — the same uniform rule as the raw members: the facade
  forwards per call the same-shape operation to the current implementation. A
  decorator that overrides the typed method is therefore observable through
  the aggregate (Rasterizer `by` delegation included). Consequence for
  `by`-wrapped decorators (the test `delegateTo` idiom): their delegation
  helper must forward the typed methods too when a typed entry must observe a
  customization (the interface default on the helper would unpack to the
  helper's raw, which forwards to `original`). Seam **default implementation
  objects are unchanged**; no `draw` typed overload on the per-pixel
  `DrawService`; `Rasterizer` needs no code change.
- **Operator set:** broader than `main` (mirrors the olc v2.30 method set,
  `olcPixelGameEngine.h` `v_2d`, L624-773), adapted to Kotlin types; degenerate
  Int-domain members may be dropped per step under the minimal-form lens with
  a `Decided:` record.
- **Conversions:** mutual — `Int2D.toFloat()` and `Float2D.toInt()`
  (component `Float.toInt` truncation toward zero, pinned).
- **`toString()` = `"($x, $y)"`** (space after the comma) on both.
- **Package:** `dev.staticsanches.kge.math.vector` (main's layout; new dir).
- Not in scope: `zeroByZero`/`oneByOne`/`by` factories, scalar-on-the-left
  (`Int * Int2D`), ordered comparison (`<`): no kernel consumer; land with the
  consumer that needs them (C8 dimension state / engine).

## Contract (decided; shapes may tighten per step)

`data class Int2D(val x: Int, val y: Int)`:

- arithmetic: `plus`/`minus`/`times`/`div` (Int2D, componentwise — `div`
  truncates like `Int / Int`), `times`/`div` (Int), `unaryMinus`.
- reference math: `perp()` = `(-y, x)`; `dot(o): Int`; `cross(o): Int` =
  `x*oy - y*ox`; `mag2(): Int` = `x*x + y*y` (Int wrapping); `mag(): Double` =
  `sqrt(mag2)` (olc returns double from int components); `area(): Int` =
  `x * y`; componentwise `min(o)`/`max(o)`; `clamp(lo, hi)` =
  `max(lo).min(hi)`.
- `toFloat(): Float2D`; `toString` `"($x, $y)"`. Equality/destructuring from
  `data class`.

`data class Float2D(val x: Float, val y: Float)`:

- arithmetic: `plus`/`minus`/`times`/`div` (Float2D, componentwise),
  `times`/`div` (Float), `div(Int2D)` (the evidenced `oneByOne / size`
  inverted-screen form, main `DimensionState`), `unaryMinus`.
- reference math: `area(): Float`; `mag(): Float`; `mag2(): Float`;
  `norm(): Float2D` (olc divides by `1/mag`; `(0,0)` yields NaN components —
  pin); `perp()`; `floor()`/`ceil()` (componentwise); `min`/`max` (Float2D);
  `dot`/`cross`: Float; `clamp(lo, hi)`; `lerp(v, t: Float)`;
  `cart()` = `(cos(y)*x, sin(y)*x)`; `polar()` = `(mag, atan2(y, x))`;
  `reflect(n)` = `this - n * (2 * dot(n))`.
- `toInt(): Int2D`; `toString` `"($x, $y)"`. Equality/destructuring from
  `data class`. Float formatting follows `Float.toString` — pin toString tests
  with non-integral components (integral floats print differently across
  targets).

Typed overloads on the raster sub-service interfaces (default bodies, each
`= <raw>(...)` forwarding to the same interface's already-declared method,
per service): `OutlineService` adds `drawLine(target, start, end, color,
mode)`, `drawRect(target, diagonalStart, diagonalEnd, color, mode)`,
`drawCircle(target, center, radius, color, mode)`, `drawTriangle(target, p0,
p1, p2, color, mode)`; `FillService` adds `fillRect(target, diagonalStart,
diagonalEnd, color, mode)`, `fillCircle(target, center, radius, color, mode)`,
`fillTriangle(target, p0, p1, p2, color, mode)`; `DrawSpriteService` adds
`drawSprite(target, position, sprite, scale, flip, mode)`. `DrawService.draw`
stays raw. Each modified service's companion `Proxy` mirrors every typed
overload with an override forwarding the **analog** method to `delegate`
(`delegate.drawLine(target, start, end, color, mode)`). `Rasterizer` exposes
them unchanged via `by` delegation.

## Files

New (commonMain): `math/vector/Int2D.kt`, `math/vector/Float2D.kt`.
Modified (additive only — interface default overloads + their companion
`Proxy` forwarding overrides): `rasterizer/service/OutlineService.kt`,
`rasterizer/service/FillService.kt`, `rasterizer/service/DrawSpriteService.kt`.
`Rasterizer.kt` and the seam default implementation objects unchanged.
New (commonTest): `math/vector/Int2DTest.kt`, `math/vector/Float2DTest.kt`,
`image/RasterizerPointOverloadTest.kt` (mirrors RasterizerTest's
`target()`/`SpriteService.create(...).applyClosingIfFailed { clear(...) }`
helper; kotest `FunSpec`). Test sources use only integer-exact or
tolerance-guarded float pins (`mag` of known squares; trig/polar with a
relative tolerance, not exact equality — cross-engine transcendental
determinism is not guaranteed).

## Steps

### 0. Int2D basics — representation, equality, destructuring, toString

- [ ] **Test (red):** `Int2DTest` — construct and read `x`/`y`; `Int2D(1, 2) ==
      Int2D(1, 2)` and `!= Int2D(1, 3)`; destructure `val (a, b) = Int2D(3, 4)`;
      `Int2D(1, -2).toString()` == `"(1, -2)"` and `Int2D(7, 8).toString()` ==
      `"(7, 8)"`.
- [ ] **Run:** fails (type missing).
- [ ] **Implement:** `data class Int2D(val x: Int, val y: Int)` with the
      `toString` override only.
- [ ] **Run:** green on jvm/js/wasmJs (node + browser).
- [ ] **Decided:** _

### 1. Int2D arithmetic

- [ ] **Test (red):** pin exact results: `Int2D(3, 5) + Int2D(2, -1) ==
      Int2D(5, 4)`; `Int2D(3, 5) - Int2D(2, -1) == Int2D(1, 6)`;
      `Int2D(3, 5) * Int2D(2, 3) == Int2D(6, 15)`;
      `Int2D(7, 9) / Int2D(2, 3) == Int2D(3, 3)` (truncation);
      `Int2D(3, 5) * 2 == Int2D(6, 10)`; `Int2D(7, -7) / 2 == Int2D(3, -3)`
      (toward zero); `-Int2D(1, -2) == Int2D(-1, 2)`; componentwise div by a
      zero component throws `ArithmeticException`.
- [ ] **Run:** fails.
- [ ] **Implement:** the operator members.
- [ ] **Run:** green on all targets.
- [ ] **Decided:** _

### 2. Int2D reference math + conversion

- [ ] **Test (red):** `Int2D(3, 4).perp() == Int2D(-4, 3)`;
      `Int2D(3, 4).dot(Int2D(2, 5))` == 26; `Int2D(3, 4).cross(Int2D(2, 5))`
      == `3*5 - 4*2` == 7; `Int2D(3, 4).mag2()` == 25; `Int2D(3, 4).mag()` ==
      5.0; `Int2D(3, 4).area()` == 12; `Int2D(1, 5).min(Int2D(3, 2)) ==
      Int2D(1, 2)`; `.max(...) == Int2D(3, 5)`;
      `Int2D(4, 0).clamp(Int2D(1, 1), Int2D(3, 3)) == Int2D(3, 1)`;
      `Int2D(-1, 3).clamp(Int2D(0, 0), Int2D(2, 2)) == Int2D(0, 2)`;
      `Int2D(3, 4).toFloat() == Float2D(3f, 4f)`.
      If a member is dropped (minimal-form), delete its pin with the
      `Decided:` note.
- [ ] **Run:** fails.
- [ ] **Implement:** the members. Decide degenerate Int members
      (`floor`/`ceil` identity, `norm` truncation, `lerp`, `cart`/`polar`/
      `reflect`) here — default: drop for Int (meaningless or truncation
      traps), record.
- [ ] **Run:** green on all targets.
- [ ] **Decided:** _

### 3. Float2D basics + arithmetic

- [ ] **Test (red):** `Float2DTest` — read `x`/`y`; equality on exact
      representables (`Float2D(1.5f, -2.25f)`); destructuring; `toString()`
      pinned with non-integral components: `Float2D(1.5f, -2.25f).toString()`
      == `"(1.5, -2.25)"`.
- [ ] **Run:** fails.
- [ ] **Implement:** `data class Float2D` + `toString` override.
- [ ] **Test (red):** arithmetic — `Float2D(1.5f, 2f) + Float2D(0.5f, 1f) ==
      Float2D(2f, 3f)`; `times`/`div` (Float2D and Float); `unaryMinus`;
      `Float2D(1f, 2f) / Int2D(2, 4) == Float2D(0.5f, 0.5f)`; div by a zero
      Float component == ±Infinity (IEEE; pin via `isInfinite`, not equality).
- [ ] **Run:** fails → implement → green on all targets.
- [ ] **Decided:** _

### 4. Float2D reference math

- [ ] **Test (red):** integer-exact pins — `Float2D(3f, 4f).mag2()` == 25f;
      `.mag()` == 5f; `Float2D(3f, 4f).perp() == Float2D(-4f, 3f)`;
      `dot(Float2D(2f, 5f))` == 26f; `cross(Float2D(2f, 5f))` == 7f;
      `area()` == 12f; `norm()` of `Float2D(3f, 4f)` == `Float2D(0.6f, 0.8f)`;
      `norm()` of `Float2D(0f, 0f)` has NaN components (`x.isNaN()`);
      `floor/ceil` (`Float2D(1.2f, -1.8f).floor() == Float2D(1f, -2f)`, `.ceil()
      == Float2D(2f, -1f)`); `min`/`max`; `clamp(lo, hi)`.
- [ ] **Run:** fails.
- [ ] **Implement:** the members.
- [ ] **Test (red):** tolerance-guarded — `lerp(Float2D(0f, 2f), 0.25f)` (from
      `Float2D(2f, 0f)`) ≈ `(1.5f, 0.5f)`; `polar()` of `Float2D(1f, 1f)`
      ≈ `(sqrt(2f), PI/4)`; `cart()` of `Float2D(2f, 0f)` ≈ `(2f, 0f)` with a
      small relative tolerance (`abs(diff) <= 1e-5f`); `reflect(Float2D(0f,
      1f))` of `Float2D(1f, -1f)` == `Float2D(1f, 1f)` exactly.
- [ ] **Run:** fails → implement → green on all targets.
- [ ] **Decided:** _

### 5. Float2D conversion + toString edge

- [ ] **Test (red):** `Float2D(1.9f, -1.9f).toInt() == Int2D(1, -1)`
      (truncation toward zero); `Int2D(3, 4).toFloat() == Float2D(3f, 4f)` and
      `Float2D(3f, 4f).toInt() == Int2D(3, 4)` (round-trip on exact
      representables); `Float2D(0.5f, 1.5f)` toString == `"(0.5, 1.5)"`.
- [ ] **Run:** fails → implement `toInt`/`toFloat` → green on all targets.
- [ ] **Decided:** _

### 6. Typed overloads on the sub-service interfaces — parity

- [ ] **Test (red):** `RasterizerPointOverloadTest` — for each primitive, draw
      on a cleared `Sprite` via the raw aggregate form and via the typed form
      (through `Rasterizer`) and assert the two pixel grids are identical
      (both endpoint orders for rects, an off-target case, Mask/Alpha mode on
      one primitive): helper
      `grid(target) = (0 until height).flatMap { y -> (0 until width).map { x
      -> target.get(x, y) } }`.
- [ ] **Run:** fails (typed methods missing on the interfaces).
- [ ] **Implement:** add to each interface (`OutlineService`, `FillService`,
      `DrawSpriteService`) the typed overload as a **default method whose body
      delegates to the interface's already-existing raw method** (unpacks the
      `Int2D` and calls it on `this`), e.g. on `OutlineService`:
      ```kotlin
      fun drawLine(
          target: MutablePixmap,
          start: Int2D,
          end: Int2D,
          color: Pixel,
          mode: Pixel.Mode,
      ): Unit = drawLine(target, start.x, start.y, end.x, end.y, color, mode)
      ```
      **and** mirror it in the same service's companion `Proxy` with an
      explicit override forwarding the **analog** method to the active
      implementation (uniform with the raw members):
      ```kotlin
      override fun drawLine(
          target: MutablePixmap,
          start: Int2D,
          end: Int2D,
          color: Pixel,
          mode: Pixel.Mode,
      ): Unit = delegate.drawLine(target, start, end, color, mode)
      ```
      Default implementation objects stay untouched (they inherit the
      interface default, whose body unpacks to their own raw override);
      `Rasterizer` unchanged; `DrawService` gets no typed overload.
- [ ] **Run:** green on all targets.
- [ ] **Decided:** _

### 7. Typed overloads — extension-contract through the aggregate

- [ ] **Test (red):** a registered decorator overriding only the **typed**
      `OutlineService.drawLine` (existing `delegateTo` pattern, helper updated
      to forward the typed members to `original`) is observed by the typed
      `Rasterizer.drawLine(target, start, end, color, mode)`; a plain override
      object (implements the interface directly, overrides only the raw
      `drawLine`, no `by`) is also observed through the typed entry — its
      inherited default unpacks to its own raw override; untouched typed
      members still delegate to the engine default;
      `KGEOverridable.Proxy.resetAll()` restores.
- [ ] **Run:** fails → implement (Proxy forwards per step 6) → green on all
      targets.
- [ ] **Decided:** _

### 8. Gate + close

- [ ] `./gradlew build --rerun-tasks` green (all suites + ktlint). API
      audit: no public member without an observable effect pinned by a test;
      no allocation introduced in the raster hot paths (the overloads unpack
      primitives before the loops).
- [ ] Two-axis review (standards + spec), fixes, verify pass of the delta.
- [ ] Decisions-log entry (this micro-plan, the per-step `Decided:` outcomes,
      the owner rulings) + roadmap update: catalogue the vector/point concept
      (math/vector, pure types, order between C6 and C7) and refresh the
      "Current state" / vector note; touch-point doc marked consumed.
- [ ] **Decided:** _

## Out of scope

`zeroByZero`/`oneByOne`/`by` factories and scalar-on-the-left (`Int * Int2D`);
ordered comparison; `MutableInt2D`; a typed overload on the per-pixel
`DrawService.draw`; changes to seam default implementations or the
`Rasterizer` object; relying on the interface default *instead of* the Proxy
analog-forwarding override (a `by`-wrapped decorator whose helper does not
forward the typed members would route typed entries to `original` — decorator
tests update their `delegateTo` helpers to forward typed); surface
`size: Int2D`; any Float2D-consuming API (surfaces/decal) — Float2D ships
typed and tested, its consumers land at R3/C8. Roadmap note: C7 text
(metrics/cursor) and R3 decals are the near consumers of these types.
