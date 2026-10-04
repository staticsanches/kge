## 2026-10-04 — Text API unification, round U1: the `KGEFont` model, the bitmap backend and `TextAddon`

The first round of the unification concept: the vocabulary that both text
backends answer, the built-in bitmap backend rebuilt on it, the single
engine-facing addon, and the removal of the old core drawing surface. Rounds:
**U1 core vocabulary + bitmap backend** → U2 bundled italic fixtures → U3 TTF
family/loading/naming/discovery/axes + measure → U4 TTF CPU draw → U5 TTF decal
draw → U6 caching/ownership hardening. Touch-point and micro-plan:
`docs/plans/2026-10-04-text-api-unification-touchpoint.md` and
`docs/plans/2026-10-04-text-api-unification-u1-microplan.md`. Round **U1b**
(`42-text-api-unification-u1b.md`) revises this round's vocabulary — names,
`Face.monospaced` and the close rule — and is where those decisions live.

### Shipped

- **The vocabulary**, `kge-core` `dev.staticsanches.kge.text`: `KGEFont :
  KGEResource` with nested `Family : KGEResource`, `Face`, `Axis` (data class),
  `Axis.Tag` / `Axis.Value` / `Size` (`@JvmInline` value classes), plus
  `Int.fontPx`, `Int.axisValue` and `Float.axisValue`. Every collection is the
  Kotlin read-only interface over a `kotlinx.collections.immutable` persistent
  collection.
- **`KGECoreFontService : KGEOverridable`**, the public construction seam,
  returning the family as `KGEFont.Family`. The family, its two faces, the
  configured font and the olc sheet painter are file-`private`; the family and
  every lease register under their own fresh private `ResourceScope.Key`; the
  family holds no reference to a lease, so a forgotten lease is never closed by
  it.
- **The bitmap backend**: `monospaced` and `proportional` share one sheet and
  one decal, which the family owns; `defaultFace === monospaced`; both answer
  `axes` empty and reject a non-empty coordinate map; a size must be a positive
  multiple of 8. The configured font's base scale is `size.px / 8`, multiplied
  by the per-call `scale`; `measureText` scales by the base only and takes no
  `scale`.
- **`TextAddon`**, the single engine-facing text addon, replacing
  `DrawStringAddon`: mutable `textFont` and `tabSizeInSpaces`, `measureText`,
  `drawText` (raw and `Int2D`) and `drawTextDecal`, each taking the font last
  with the principal font as the default. Its roles include `HasResourceScope`,
  whose consumer is U3's TTF loading ergonomics.
- **`Engine : TextAddon`**: the principal font is adopted from the core family's
  `defaultFace` at `8.fontPx` inside `start()`, so the lease is released with
  the run's scope; `tabSizeInSpaces` starts at 4 and rejects a non-positive
  assignment; reading `textFont` outside a run fails fast.
- **Removed, not deprecated**: `DrawStringService`, `DrawStringAddon` and every
  `*Prop` name, with 14 files deleted — the two production types, the seven
  legacy specs and the five legacy golden references. The TTF surfaces
  (`TtfTextService`, `TtfDrawStringAddon`) survive until U3–U5 replace them, so
  the round closes the core half of touch-point item 36 only.
- **Tests replaced, not ported**: `CoreFontModelTest`, `CoreFontFamilyTest`,
  `CoreFontMetricsTest`, `CoreFontDrawTest`, `CoreFontDecalTest`,
  `golden/CoreFontGoldenTest`, `TextAddonTest`, `EngineTextFontTest` and the
  benchmark's `MergedTextAddonTest`. Eight golden references were **authored**,
  never recorded from the new code: seven byte copies of the committed
  references under new names, and `mono-16-scale-2` as the exact 4x nearest
  replication of `mono.png`. `payload-mono-8` converts the old internal-payload
  golden into a public-API one by drawing the whole 96-cell payload through the
  mono face, so deleting the sheet golden loses no coverage.
- **The benchmark**: `MergedDrawStringService` becomes `MergedTextAddon`, a
  **host** decorator over `TextSceneTarget` that forwards every role and
  overrides only `drawTextDecal`; `TextSceneTarget : TextAddon`. The merged
  lever keeps its measurement: one triangle-list instance per run.

### Decisions

- **`Int.axisValue` is a design coordinate.** It is `Value.ofOrNull(toFloat())`,
  identical to `Float.axisValue`, so `1.axisValue == 1f.axisValue`; the raw
  16.16 integer is reached only through `Value.of(raw: Int)`. The first
  implementation had the two extensions in different units under one name — a
  silent 65536x error at a call site, with the difference invisible to the
  compiler.
- **`Axis.Value.toString` uses no `Long`.** It splits the two's-complement
  halves (`raw shr 16`, `raw and 0xFFFF`) and recovers the magnitude parts with
  the borrow a negative value needs, so `-raw` is never formed; the digit loop's
  remainder stays below 65536, making its constant division a shift and a mask.
  The formatter's `numeral` remains a local function of `toString`, not a
  member.
- **`Face.font` is `suspend`.** A configuration's native state is created through
  the same seam as a family's face, and on the web that seam awaits the
  process-wide FreeType module; propagating the suspension keeps one construction
  path instead of a parallel synchronous one, and it gives the configuration cache
  a lock that exists on every target rather than a JVM-only one with a no-op web
  actual.
- **A public interface cannot back a mutable property**, so `TextAddon`'s
  `textFont` and `tabSizeInSpaces` are abstract and the host enforces
  positivity; `Engine` is the host the contract is pinned on. Assigning them is
  not thread-guarded, matching `pixelMode`/`decalMode`.

### Divergences and retained differences

- **The shortest spelling is not target-independent.** Kotlin/JS
  `String.toFloat()` is `toDouble().unsafeCast<Float>()` and does not demote to
  binary32, while the JVM and WasmJS do, so `toString`'s acceptance test admits
  a different candidate set there: `Value.of(Int.MAX_VALUE)` spells
  `32767.9999847412109375` on JVM/WasmJS and `32767.99998` on `js`. The
  touch-point's property — the shortest spelling that round-trips — holds on
  every target, and no consumer observes the difference; the tests therefore
  never pin `Int.MAX_VALUE` as a string.
- **A carriage return is not normalized.** On the mono face it is an ordinary
  cell: it advances 8 px and paints nothing, because the sheet is sampled
  `NORMAL` and its out-of-bounds read is transparent. On the proportional face
  it indexes the 96-entry spacing table below its base and fails. This is the
  pre-existing `C7` behaviour the touch-point retains as
  "unsupported/input-specific text"; the mono side is pinned, the proportional
  side is recorded here rather than pinned as a contract.
- **The core family answers `axes` with an empty map** and rejects a non-empty
  coordinate map, so the bitmap font never fakes a variation axis. This is the
  touch-point's deliberate reversal of the variable-axes findings §9, recorded
  there; the unified signature is what it buys.
- **`tabSizeInSpaces` needed a forced override in the benchmark.** `Engine` now
  carries `TextAddon`'s mutable property while `TtfDrawStringAddon` declares the
  same name as a `val`, so `FpsBenchmarkEngine` and `TtfCarrierUploadTest`'s
  private probe engine resolve the ambiguity with an explicit
  `override var ... super<Engine>`. Compile-forced and behaviour-preserving; it
  disappears when U3–U5 replace the TTF addon with `TtfFontAddon : TextAddon`.

### Not in this round

Anything TTF: payload families, atomic multifile loading, name resolution,
`fvar`/`name` discovery, axis coordinates other than the empty map, native
configuration caching and reference counting, the lease leak-report contract
(the core lease holds no native payload), and the TTF drawing paths. The
bundled italic fixtures are round U2; the old TTF public names remain until the
rounds that replace them. **Round U3 also owns** adding
`kotlinx.collections.immutable` as an `implementation` dependency of
`kge-text-ttf`, which the touch-point's collection convention requires and which
neither this round nor U2 declares. **Round U6 owns** the configuration cache's
single-flight: a per-family `kotlinx.coroutines.sync.Mutex` over a suspending
construction, non-reentrant, with the guarded region's boundary and its reason
specified in the touch-point's ownership section.

### Verification

`tools/gradle build` green (the gate: every target's tests, ktlint, the
metadata/kLIB compilation and `buildSrcCheck`). Core suites report 740, 775 and
775 tests on jvm, js and wasmJs with no failures — the three jvm skips are the
pre-existing GLFW-window smoke tests; the benchmark reports 21 on jvm and 22 on
wasmJs. Round U1b, which follows, reports 744, 779 and 779. The eight authored
references reproduce byte-for-byte. The
round closes only when the two review axes pass on this exact staged tree, and
the marker names the report that carries its hash.
