## 2026-09-09 — vector/point concept: `Int2D`/`Float2D` + typed raster overloads (closed)

Touch-point (2026-09-09, owner) decided the shape and scope; micro-plan
`docs/plans/2026-09-09-vector-point-microplan.md` (touch-point material in
`2026-09-09-vector-point-touchpoint.md`). Two-axis review passed (spec +
standards, no Critical/Important; reports machine-local). Decisions at close:

- **Two concrete pure `data class`es — no generic.** `Int2D(Int, Int)` /
  `Float2D(Float, Float)` in `dev.staticsanches.kge.math.vector` (`data`
  equality/destructuring; `toString` = `"(x, y)"`). No `v_2d<T>`-style generic
  (Kotlin has no arithmetic generics — a generic would need `Number` casting,
  the exact per-op cost that also ruled out `Long` packing), no shared
  interface (no polymorphic consumer yet), no `MutableInt2D` (no consumer;
  revisit at R6 if text proves mutation). `Long` packing rejected: Kotlin/JS
  has no 64-bit primitive (`Long` is a two-`Int` object on the ES5 backend, a
  `BigInt` otherwise — stdlib `boxedLong.kt`/`longAsBigInt.kt`), and no
  per-pixel vector consumer exists (unlike `Pixel`), so the JVM benefit is
  moot. Broad olc v2.30-derived operator set; the degenerate Int-domain
  members (`floor`/`ceil` identity, `norm`/`lerp`/`cart`/`polar`/`reflect` on
  `Int`) were dropped under the minimal-form lens.
- **Typed `Int2D` raster overloads = interface defaults + companion `Proxy`
  analog forwarding.** Each of `OutlineService`/`FillService`/
  `DrawSpriteService` gained typed overloads as interface default methods that
  unpack and call the existing raw method on `this`; each companion `Proxy`
  mirrors them, forwarding the **analog** typed call to `delegate` (the
  uniform facade rule — never unpacking at the Proxy). Overriding only the
  typed member is observable through the aggregate, and a plain raw-only
  override object is too (its inherited default unpacks to its own raw). Kotlin
  `by` delegation forwards the new interface-default members on 2.4.10 —
  verified empirically (`typedCalls == 1` through `Rasterizer`); the step-7
  tests are the tripwire if a future backend stops generating the forwarders.
  Per-pixel `DrawService.draw` stays raw; seam default implementation objects
  and `Rasterizer` unchanged.
- **Verified facts — the JS `Int`/float divergence is engine-wide.** Kotlin/JS
  `Int / Int` on a zero divisor does NOT throw (it yields a defined-but-non-JVM
  result) while JVM/wasmJs throw. `Int2D.div` pins the uniform
  `ArithmeticException` on all targets via a private `checkedDiv`, but the
  divergence applies to **any raw `Int` division** in future engine code:
  code that divides by a runtime value must guard or accept the JS behavior.
  Kotlin/JS float math is not float32-rounded (JS computes in float64), so
  exact float pins must use values exactly representable in both and
  cross-engine-transcendental results need tolerance. `Float2D.div(Int2D)`
  keeps IEEE semantics on a zero component (±Infinity/`NaN`), asymmetric with
  `Int2D.div`'s throw — stated in the KDoc.
- **Gate:** `./gradlew build --rerun-tasks` green on all five suites (jvm, js
  node + browser, wasmJs node + browser) and ktlint. Deferred minors at close:
  `clamp` parameter naming (`low`/`high` vs the micro-plan's `lo`/`hi`
  shorthand); ~120 lines of test-helper duplication across `RasterizerTest`
  and `RasterizerPointOverloadTest` (the established local-helper pattern; a
  shared fixture would remove it). Delivered as a single commit (implementation
  + tests + docs) on top of the C6 close.

