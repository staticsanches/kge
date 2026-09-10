## 2026-09-03 — C2 (resource lifecycle, T1): touch-point decisions + close facts

### Touch-point decisions (owner)

The open items of the T1 macro requirement were decided at the C2 touch-point
(this session):

- **Report mechanism — `LeakReporterService` (T2 service, owner).** The
  capability "where a leak is reported" is engine-fixed behavior with a
  consumer override, per principle 1 — the second real consumer of the T2
  mechanism after T3. `interface LeakReporterService : KGEOverridable` with
  `report(representation: String)`; companion object extends the Proxy (the T3
  facade shape); engine default is a **private** `LoggingLeakReporter` via
  kotlin-logging (error level) — not referenced from the interface KDoc (the
  T3 review nit); the log message is `"Resource $representation was not
  closed and is potentially leaking its resources"` — the T1 "unclosed"
  vocabulary, deliberately divergent from `main`'s "was not cleaned" wording
  (minimal-form: the exact log text is not a contract, the report payload is).
  Extension-contract test proves the override.
  Name: *Service suffix* pattern; package `dev.staticsanches.kge.resource`
  beside the contract (the T3 "no new subpackage for one type" rule).
- **Scope — the four types only (owner).** `KGEResource`, `ResourceWrapper`,
  `KGELeakDetector`/`KGECleanable`, `KGECleanAction` + essential factories.
  Deferred to their future consumers (evidence in `main`): `KGEInternalResource`
  (E1 window/layer), `letClosingIfFailed`/`applyClosingIfFailed` (E1 window),
  `use`/`andThen`/`invokeIfFailed`/`toCleanerProvider` (R9 GL wrappers),
  the `component1`/`component2` destructuring sugar (no consumer requires
  it) and the wrapper `toString()` state suffix (`"(released)"` — no consumer
  reads it). Full `main` parity rejected (minimal form, YAGNI).
- **Detection/testability split (owner).** The platform registration is a
  THIN seam: `internal expect fun registerCollectionTrigger(obj, onCollected)`
  returning a `KGECleanableHandle` (unregister). Everything testable lives in
  the common module: `KGEResourceCleanableState` (cleaned flag, action
  exactly-once, report exactly-once, race-safe via `AtomicReference.exchange`),
  and the deterministic entry `onCollectionObserved()` (internal) on the
  wrapper/cleanable — tests drive it instead of relying on GC timing.

A note on the review loop below: the close review pass belongs to the
concept close in the normal flow; the entry is written at close and the
owner runs the Hunk review (their standing loop — owner commits and pushes;
delivery is commit-ready work).

### Verified facts (add-time checks, this session)

- **`js.memory.FinalizationRegistry` no longer exists in the Kotlin/JS
  stdlib.** Verified: compile probe on js + wasmJs (`Unresolved reference
  'js'`), and klib/sources listing of kotlin-stdlib-js 2.1.21/2.4.10 and
  kotlin-stdlib-wasm-js 2.4.10 (no `package_js.memory`). `main` (Kotlin
  2.1.20) still had it. It is now provided by
  `org.jetbrains.kotlin-wrappers:kotlin-js` — declared in `webMain` for both
  js and wasmJs targets (module metadata: `kotlin-js-js` and
  `kotlin-js-wasm-js` variants; confirmed in sources of 2026.8.5 and
  2026.9.0). Add-time: current release **2026.9.0** (repo1 check) — built
  with Kotlin **2.4.10** (kotlin-tooling-metadata.json), matching the branch.
- **kotlin-logging: current release 8.0.4** (repo1 check; `main` pinned
  7.0.6). Published for wasmJs (`kotlin-logging-wasm-js`, checksum 200),
  built with 2.1.21 — fine as an older-metadata dependency.
- **kotlin-logging 8.0.4 drops the compile-scope slf4j-api dependency.**
  The jvm POM/module declare only kotlin-stdlib; `NoClassDefFoundError:
  org/slf4j/LoggerFactory` at runtime. Fix: the engine declares slf4j-api
  itself — current **2.0.18** (2.1.0-alpha1 is `latest`, alpha; the stable
  release line is 2.0.18), jvmMain scope. Recorded so the next add-time
  check does not re-discover it.
- **`kotlin.uuid.Uuid` is stable in 2.4.10** (`@WasExperimental`, no opt-in
  needed — the `@ExperimentalUuidApi` file in stdlib is history; first
  compile without the annotation succeeded on jvm, then full gate).
- **The wasmJs-observation question (spike verdict):** probes on all targets
  never observed a FinalizationRegistry callback within the poll window with
  heap-pressure churn; the pure-node control (with `--expose-gc` +
  heap-busting, `new ArrayBuffer(32MB)` rounds) fired the callback quickly.
  Conclusion: V8 does deliver finalizers; the *kotest/node harness* does not
  force GC reliably, so callback-assertions are not deterministic test
  material on ANY web target. Design consequence: the web actuals register
  the same way on js and wasmJs (one `webMain` file — uniformity per parity
  floor), with KDoc noting the best-effort nature; **no no-op on wasmJs** —
  the owner's fallback (no-op if the spike failed) applies only if the
  mechanism itself were unproven; the harness limitation is not platform
  proof, and the same code exists on js anyway (no extra wasmJs-unsafe code).

### Implementation (TDD per feature, jvmTest per cycle, full gate at close)

- **F1** KGEResource + KGECleanAction(+factory) — red: compile (type
  missing); green.
- **F2** LeakReporterService + LoggingLeakReporter — red: compile; green;
  then the slf4j-api runtime discovery (see facts) fixed the `jvmTest`.
  Service guards (decorator-on-original, last-wins, resetAll) passed on
  first run by design (T2/T3 precedent: their subjects proven by earlier
  cycles).
- **F3** KGEResourceCleanableState + leak path — 5 tests; the state machine
  races (`clean` wins / collection wins) both covered. `exchange(null)` is a
  member (imports of `kotlin.concurrent.atomics.exchange`/`fetchAndSet`
  failed — member, like `load()`/`store()`, per #26).
- **F4** registerCollectionTrigger expect/actual (JVM Cleaner; web
  FinalizationRegistry via kotlin-js) + KGELeakDetector + ResourceWrapper —
  tests: distinct uuid, close-once, fail-fast (message aligned with `main`:
  "has already been released and can not be used"), leak-vs-close both ways.
  Red cycles: `private` on a local fun (invalid) — fixed; `shouldBeTypeOf`
  on a null-suppressed value produced "Cannot infer type" + at 758 a matcher
  parse error, switched to the `shouldThrow` idiom used by the T2 suite;
  JVM-only `StringBuffer` in commonTest — switched to `"x"` (String) after
  the wasmJs compile red.
- **GC-integration test (jvmTest only, best effort)** — an unclosed wrapper
  reported within a 15s poll window (System.gc + 50ms sleeps;
  `System.runFinalization()` dropped — deprecated since Java 18, it was only
  a hint). Green in 73ms on the first run. Not part of the commonTest parity
  suite (GC timing is not deterministic by design); the commonTest proofs
  are the deterministic seam.

Close review pass: the round-1 two-axis review ran on the staged tree before
commit — one Standards judgement call (the `registerCollectionTrigger` KDoc
overstated the unregister guarantee vs the JVM `Cleaner.clean()` reality; fix:
one-sentence KDoc reword + the load-bearing-order comment at the `clean()`
site), verify pass CLEAN, no Spec findings, gate numbers corroborated.

Owner Hunk review round (post-commit, 2026-09-03): the `as
KGEResourceCleanable` cast in `ResourceWrapper` was questioned; **kept**
(owner decision). Rationale: `KGELeakDetector.register` is not consumer API —
its sole caller is the wrapper factory (`ResourceWrapper.kt:51`); the
invariant is documented at the cast site and was verified by the round-1
review (`register` is the only `KGECleanable` construction path in the
module). Exit if a second construction path ever appears: `register` becomes
internal and returns the internal concrete type, moving the invariant from a
runtime cast into a compile-checked signature — no change to any public
surface. Recorded so a future session does not re-open it without new
evidence (ex: a second construction path or a consumer-facing register).
A second note in the same round: the dead binding in
`KGEResourceCleanableState.onCollected` — `val action =
actionRef.exchange(null) ?: return` kept a value that is deliberately
discarded (the collection path never runs the action). Kotlin's compiler
does not flag unused locals by default (IDE inspection only), which is why
the gate logs stayed silent; fix applied (`if (actionRef.exchange(null) ==
null) return`), semantics unchanged, gate re-run green with zero compiler
warnings (58/62/57, 0 failures).

Gate: `./gradlew :kge-core:allTests ktlintCheck --rerun-tasks` green (fresh
run, XML counts: jvmTest **58**, jsNodeTest **62** (js adds kotest
discovery), wasmJsNodeTest **57**; zero failures on all targets). Baseline
shift vs the #29 gate (41/46/41): +17 on jvm (16 new commonTest — 2
contract + 4 service + 5 state machine + 5 wrapper — plus the 1 jvm-only
GC integration test), +16 on js/wasmJs. ktlintCheck clean (one format pass:
multiline-expression-wrapping on the override object expressions).

IDE inspection sweep (owner-run, 2026-09-03, Inspect Code over kge-core):
three real findings, all fixed — the KDoc link `[unregister]` unresolved,
now `[KGECleanableHandle.unregister]`; the redundant SAM constructor in
`JvmCollectionTrigger` (`Runnable { }` → trailing lambda); the
`@Incubating` `repositories(Action)` use in `settings.gradle.kts`,
suppressed with `@Suppress("UnstableApiUsage")`. The rest of the sweep is
IDE-side noise, not source defects: `kotest.kt` "redundant public" hits
(KSP-generated discovery code under `build/`, excluded from ktlint for the
same reason); the KMP "unnecessary module dependency" inspections (IDEA's
module model does not model the source-set dependency hierarchy — false
positives by design); the spelling dictionary hits for project terms
(Koin, kotest, Kover, wasm, klib, lwjgl, actuals, gitignored, unpushed,
externref, RRGGBBAA, Transversals, nulled, githooks, herdr, shasum, esac,
the `gradlew.bat` cmd words, the owner's own surname in LICENSE) — owner
adds these to the IDE custom dictionary; no action in-repo. Intentional,
no action: the `gray`/`grey` CSS alias pairs in the #24 colors record,
the "newer Gradle minor" hint (9.5.0 pinned by project rule) and the
LICENSE `©` suggestion.

