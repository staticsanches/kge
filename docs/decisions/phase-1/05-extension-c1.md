## 2026-09-01 — C1 (extension mechanism) closed (touch-point decisions + add-time verifications)

### 26. C1 (extension mechanism) — touch-point decisions + close facts

The open item of the facade contract (activation policy) was decided at the
C1 touch-point (this session):

- **Activation policy — explicit** (owner). The engine activates the context
  on create (`kgeDefaultModule` + `kgePlatformModule`) and deactivates on
  destroy; double activation and any access before activation fail fast
  (`IllegalStateException`); deactivate is idempotent (safe for unconditional
  engine-destroy calls).
- **Naming — `KGEContext`, not `ActiveContext`** (owner): KGE prefix keeps
  `main`'s convention; "active" is redundant since there is no user-visible
  "inactive" context. Roadmap "Facade contract" text updated to the name and
  the decided policy.
- **Proof — mechanism + test-defined service** (owner): C1 ships the machinery
  only; the extension-contract test (`KGEContextExtensionTest`) defines its own
  exemplar (`Translator`: default impl, internal `kgeOriginal` qualifier
  binding, delegating `DecoratorTranslator`, stateless per-call facade with
  `original`). The first real service lands with its consumer (T3 pixel display
  formats → C8 clock). No provisional API in `commonMain` beyond `KGEContext`
  + modules + qualifier.
- **koin-test not added in C1** — tests drive `KGEContext` directly (the
  roadmap's "scope helper" option); the wasmJs koin-test question is resolved
  as deferred to the first consumer that needs `loadKoinModules` semantics.
  `koin-core` lands and **runs on wasmJs** (verified by the wasmJs node test
  run of the new suites — koin-core-wasm-js artifact present in resolution).

Add-time verifications (Koin 4.2.2, this session):

- **`kotlin.concurrent.AtomicReference` does not exist in KMP common on KGP
  2.4.10.** The `kotlin.concurrent` package in the stdlib 2.4.10 jar holds
  only Locks/Threads/Timers/Volatile; the common atomics API lives in
  `kotlin.concurrent.atomics`: `AtomicReference` with `load()`/`store()`/
  `exchange()` (no `get`/`set`/`getAndSet`/`value` in common — `value` is
  JVM/native-only) and `compareAndSet`/`compareAndExchange`, all still
  `@ExperimentalAtomicApi` (opt-in required; extension fns `update`/
  `updateAndFetch`/`fetchAndUpdate` since Kotlin 2.2). Used with one
  `@OptIn` on the object. Sources: the stdlib jar listing + the public API
  reference.
- **Koin 4.2.2 last-declared-wins without an override flag — yes.** Verified
  from the koin-core sources jar: `KoinApplication.allowOverride = true` (new
  applications), `Koin.loadModules(..., allowOverride = true, ...)` default;
  resolution semantics covered by tests (module-order override + runtime
  `loadModules` override both exercise it).
- **Koin current release at add-time: 4.2.2** — repo1.maven.org
  `koin-bom`/`koin-core` metadata (`<release>4.2.2</release>`, checked
  2026-09-01); the planned pin stays (no known problem).
- **`koinApplication {}`/`KoinApplication.close()`/`koin.get(qualifier)`/
  `named(String)`** confirmed in the common API (koin-core sources jar), used
  exactly as shaped. No `GlobalContext`/`startKoin` anywhere.

Close facts: TDD with the micro-plan (test → red → implement → green);
jvmTest red was the expected missing-API compile failure, then the full cycle;
the extension-contract test passed on first run by design (its subject — the
pattern — was built in the previous cycle; it guards the proof, it is not a
new-red cycle). **Close review pass** — two-axis review (Standards: no hard
violations; 3 actionable judgement calls — duplicated module fixtures,
repeated `load()?.koin ?: error` shape, e.g. — plus Spec: 2 findings —
`activate` was check-then-store (non-atomic, concurrent activation leaks the
first application) and the 5th extension test contradicted itself). All
findings fixed in the same commit: `activate` is now build-then-
`compareAndSet` (on CAS failure the new application is closed and
`IllegalStateException` is thrown), the contradictory test removed, the
`requireKoin()` helper unifies the error shape, fixtures extracted.
Adversarial verify pass of the fix delta: CLEAN (verdict + evidence in
`~/.claude/kge/reviews/c1-round1-verify.md`).
Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green —
allTests executed (jvmTest 41, wasmJs node 41, js node 48 — the js figure
again adds kotest container/discovery suites; 0 failures; counts read from
the XML reports, see item 15; 42 → 41 with the removed test). ktlintFormat
applied twice (multiline-expression-wrapping + no-unused-imports, then
function-signature on `requireKoin`). One commit for the concept, per the
owner's single-commit decision (see #24); the CI yarn fix landed as its own
commit before it (#25).

### 27. C1 (extension mechanism) — identity-semantics amendment

Reviewing the unpushed C1 commit surfaced a contract gap in the proven
`original` pattern: the exemplar module bound the default twice (unqualified
`single` + `single` under `kgeOriginal`), so the container held **two
singletons of the same default implementation** — the facade's `translate()`
and `original` never referred to the same object. The contract is identity
**semantics**: the default and `X.original` must be the same instance,
so stateful services need no statelessness exception and a decorator delegates
to the exact default object the engine would have used.

Fix (same concept; per the single-commit decision #24 it lands in the C1
commit, amended while unpushed): the canonical instance is bound under
`kgeOriginal` and the unqualified binding is an alias to it
(`single<X> { get(kgeOriginal) }`). The mirror shape (canonical unqualified,
qualifier as alias) was rejected: after decoration the qualifier alias would
resolve `get()` to the decorator itself, forming a
decorate→original→decorate cycle. `KGEContextExtensionTest` gains the missing
identity guard ("the default binding and the original are the same instance",
`shouldBeSameInstanceAs`): first red was a type-inference accident (reified `T`
of the right-hand `resolve(kgeOriginal)` inferred as `Any` →
`NoDefinitionFoundException`); with the explicit type argument the true red
followed (`AssertionFailedError` — two distinct instances), green after the
flip. `kgeOriginal` KDoc and the roadmap "Original binding" wording updated
(the contract no longer implies stateless implementations).

