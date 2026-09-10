## 2026-09-02 — T2 (extensible service mechanism) redesign: `KGEOverridable` supersedes `KGEContext`

At the first T2 touch-point the extension mechanism was redesigned (owner
decision). The engine's services are **fixed engine behaviors** — defaults
defined by the engine — and the mechanism exists for one purpose: letting a
consumer replace one at runtime. `KGEContext` is **superseded**, not extended
(the two do not coexist — owner decision); the C1 contract of #26/#27 is
replaced.

### Decided

- **Concept — no generic DI container.** Engine wiring is plain object
  composition; consumers who want DI manage their own Koin. The mechanism's
  internal Koin (process-global, hidden — the defaults catalog + current
  implementations) is machinery, not a user-facing registry.
- **Shape.** `KGEOverridable` (marker interface) + `KGEOverridable.Proxy<O>`:
  the service facade companion extends it (`type`, `original`); per-call
  resolution via a `protected` `delegate` getter; `override(impl)` public —
  last-declared-wins; `resetAll()` **internal** — engine `onDestroy` and test
  teardown restore the engine defaults from the catalog; no per-service undo,
  no scoping. Package `dev.staticsanches.kge.overridable`.
- **Facade delegation — shape A** (owner choice after examining the options):
  hand-written forwarders on the facade companion (`override fun m() =
  delegate.m()`) — compile-safe, no magic. Rejected: `by` with a service
  instance (captured once — the main-era freeze; `by` is dynamic only when the
  captured object is a re-resolving gateway, i.e. the C1 proxy-object pattern
  — rejected as a relocation of the same one-liners); interface-default
  delegation (a missing override would silently inherit the facade delegation
  → runtime recursion instead of a compile-time error); codegen (KSP forces
  every consumer module to apply a processor; an IR compiler plugin adds a
  version-locked artifact; `Proxy<O> : O` is illegal — a type parameter
  cannot be a supertype — and KMP common has no reflective dynamic proxy).
- **Original — identity by construction.** The default is one instance passed
  to the Proxy: `original` IS the default, so #27's double-binding problem
  cannot occur (no qualifiers, no per-service default module + alias, no
  `kgePlatformModule`). The `X.original` facade name survives.
- **Activation policy — implicit (supersedes #26's explicit + fail-fast).**
  Defaults register on first facade touch; the mechanism works without the
  engine — deliberate: engine-fixed behavior is always present (T3's
  `Pixel.toString()` in logs/tests without an engine; #26's fail-fast use
  outside activation is replaced by always-available). Lifecycle: engine
  `onDestroy` → `resetAll`; no leakage to the next engine instance.
- **Platform defaults — `internal expect`/`actual`** implementation objects
  per target (first real need: S1/R4-style platform-backend services). The
  extension-contract proof exercises the actuals on all targets — one actual
  per target declared in test source sets, asserted from commonTest.
- **kotlinx-collections-immutable 0.5.2** — incoming dependency: the defaults
  catalog becomes an `ImmutableList` in the common `AtomicReference` (the
  sketch's mutability TODO dies). Add-time check: repo1.maven.org
  `<release>0.5.2</release>` (checked 2026-09-02).
- **Sensitive API.** `@KGESensitiveAPI` reintroduced (main's pattern —
  `dev.staticsanches.kge.annotations`, `@RequiresOptIn(Level.ERROR)`,
  BINARY; project-wide opt-in inside `kge-core`) on `override` and
  `resetAll`, each with risk docs; the public `override` doc does not
  reference the internal `resetAll`.
- **Proof.** `TranslatorService` returns as the contract exemplar in
  `commonTest` — `KGEOverridableExtensionTest` (supersedes
  `KGEContextExtensionTest`); `KGEContext`/`KgeModules` sources and
  `KGEContextTest` are removed at the concept close (history is the archive;
  the public-surface change is accepted at the checkpoint per the
  no-throwaway rule).
- Tooling notes: `org.koin.dsl.bind` is the idiomatic import (the
  `org.koin.plugin.module.dsl.bind` variant also resolves in koin-core 4.2.2 —
  non-infix, compiler-plugin support); ktlint function-signature wraps 2+
  parameter declarations (the forwarder style follows it as usual).

### Close (2026-09-02)

TDD per concept flow, one red→green cycle per feature (default resolution,
override, original/decorator, last-wins guard, resetAll guard) with `jvmTest`
per cycle, then the full gate. Tooling findings during the cycles (correct the
add-time expectations above):

- Koin 4.2.2 `single<T>` is reified-only: a binding for a `KClass` held in a
  type parameter uses `single<Any> { }.bind(type)` with the plugin-module
  non-infix `bind` (`org.koin.plugin.module.dsl.bind`) — the infix
  `org.koin.dsl.bind` is same-type-only (`KoinDefinition<out S>.bind(KClass<S>)`)
  and cannot take a secondary type.
- kotlinx-collections-immutable 0.5.2: `ImmutableList` is the read-only
  interface and the persistent operations (`add`) are on `PersistentList` — the
  defaults catalog is typed `PersistentList<Module>`. Implements the design's
  "immutable catalog" (the `ImmutableList` word in the design above is the 0.4
  naming; the design intent is unchanged). In 0.5.2 the `MutableCollection`-
  styled ops are `@Deprecated` — the persistent variants are `adding`/
  `addingAll` and `putting`/`puttingAll` (`ReplaceWith(...)`); the registry
  uses them. After the second Hunk comment the registry is a `PersistentMap`
  keyed by service type (`KClass<*>`): the duplicate-service guard is O(1)
  (`containsKey` — was an O(n) scan over the list) and `resetAll` iterates
  the values.
- `AtomicReference.update` requires the explicit
  `import kotlin.concurrent.atomics.update` (extension function, not a member).
- ktlint: `no-empty-first-line-in-class-body` (no blank after `{`) and
  `no-blank-line-in-list` (no blank lines inside value parameter lists — the
  `original` KDoc sits directly above the parameter).
- ktlint-gradle 12.3.0 wiring gap: the `ktlint { filter { } }` exclusion reaches
  only the check tasks; the format tasks (`ktlintFormat`, run by the pre-commit
  hook) see the KSP-generated discovery files under `build/generated/` and fail
  on them (observed with the new `jsTest`/`wasmJsTest` KSP outputs; upstream
  issues #751/#743). Fix: the same exclusion is wired per task —
  `tasks.withType<BaseKtLintCheckTask>().configureEach { exclude { … } }`
  (both task types implement `PatternFilterable`). The format tasks are
  incremental: stale snapshots (`build/intermediates/ktLint/*-snapshot.bin`)
  recorded before the fix replay the generated file — clearing them made the
  exclusion effective; the check gate stays the authority.

Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green (fresh
run per item 15). Counts verified from the XML reports (before the Hunk
round below) — jvmTest 35 tests (`KGEOverridableExtensionTest` 5, failures
0), jsNodeTest 40 (extension 5/0/0 plus kotest discovery 1),
wasmJsNodeTest 35 (extension 5/0/0); `SmokeTest` still 1/0/0 on every
target. The C1 suites are gone with the C1 sources — the
count drop is the supersession, not a regression. The last-declared-wins and
resetAll guard tests passed on first run by design (their subjects were proven
by the earlier cycles); they guard the proof, like the C1 identity guard.

**Close review pass** (2026-09-02) — two-axis review (Standards + Spec) of the
staged diff vs HEAD; findings fixed in the same changeset: the binding idiom
extracted (`moduleFor` — duplication between `init` and `override`), the
`FunSpec` blank line removed (house style), `type` renamed `serviceType`
(private — registry machinery; the facade API exposes `original` only; owner
decision at the round-4 review), and the per-platform proof assertion
de-anchored — each target declares the expected default output as its own
literal (`translatorExpectedDefault` actuals, declared independently of the
implementation), so a wrong or copy-pasted literal fails that target's test
(the original pairing made the assertion self-referential: expected and
default came from the same actual, resting any wrong literal). Spec
axis verified the close facts from the XML reports (counts, supersession,
0.5.2 resolution); no missing/crept/wrong requirement. Adversarial verify
pass of the fix delta: CLEAN (verdict + evidence in
`.claude/kge/reviews/t2-round1-verify.md`).

### Owner Hunk review round (2026-09-02 — folded into the same concept commit)

Two review comments on `KGEOverridable.kt`; refined before any approval:

- **Active implementation held directly.** Each Proxy holds its own
  `AtomicReference<O>` of the active implementation: `delegate` is a single
  volatile read, `override(impl)` a store (last-declared-wins naturally),
  `resetAll` one `clearOverride()` per registered proxy — no container
  rebuild, no module catalog. The hidden Koin machinery is gone: the per-call
  path drops ~2 hashmap lookups to 1 field read (matters in hot-loop
  services — the S1 allocator provider, raster/renderer seams), and
  `koin-core`/`koin-bom` leave the project (the Koin add-time and `bind`
  findings above are history; the extension contract and its proof are
  unchanged).
- **Engine-declared services only.** The Proxy constructor is `internal` —
  consumers override engine services, they do not declare new ones. The
  `register` guard runs in one atomic step: the `require` lives inside the
  `AtomicReference.update` lambda, so the check-and-add linearizes as a
  single CAS (`IllegalArgumentException` on a duplicate service type); the
  extension-contract proof gains that case (5 → 6 tests; final counts below).
- The facade-contract "banned held active instance" wording is adjusted: the
  active implementation lives in the mechanism's swappable registry (atomic —
  no staleness by construction), it is never held by a facade.

Gate after the refinement: `./gradlew :kge-core:allTests ktlintCheck
--rerun-tasks` green — counts verified from the fresh XML reports: jvmTest 36
(extension 6/0/0), jsNodeTest 41 (extension 6/0/0 + kotest discovery 1),
wasmJsNodeTest 36 (extension 6/0/0); `SmokeTest` 1/0/0 everywhere. One
commit for the concept (owner decision #24), amended to fold the refinement.

### Round-4 review (post-commit 2026-09-02) — the committed tree vs the agents

The Hunk refinement delta (100+/47-) merged via the 19:15-19:22 amends had
never been re-reviewed by the review agents: round-1 verify (18:41) predates
it and the review gate could not tell (the marker was hand-refreshed 16s
before the final amend; nothing tied it to an agent report of that tree).
Post-commit two-axis review of `06ad04b` — evidence
[`t2-round4-twoaxis.md`] (report carries the reviewed `tree:` hash so the
gate can prove coverage):

- **Standards** — no hard violations. One record-vs-code finding,
  self-confirmed in the commit: the de-anchor sentence in the Close review
  pass above overstated the code — the assertion interpolated
  `$translatorPlatformMark` from the same actual that produced the default
  (self-referential; a copy-pasted actual still passed that target's test).
  Fixed for real: each target declares `translatorExpectedDefault` — the
  expected default output, as its own literal in its own file
  (`TranslatorExpected.kt`) — and the test asserts it verbatim
  (`shouldBe translatorExpectedDefault`). Proven by red proof: a wrong
  literal fails only that target (wasmJs 3 fails, restored); gate counts
  unchanged (36/41/36; `jsBrowserTest`/`wasmJsBrowserTest` 41 x2 in the same
  run, 0 failures).
- **Spec** — nothing missing or crept; one surface wording loose end: the
  `type, original` parenthetical read as a public `type`. Owner decision:
  `serviceType` stays private (registry machinery; the facade API exposes
  `original` only) — the roadmap wording above carries the precision.
- Fix-delta adversarial verify: CLEAN (verdict + evidence in
  `t2-round4-twoaxis.md` — fix delta = `git diff 06ad04b`).

