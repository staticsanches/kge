## 2026-09-01 — CI Windows yarn.lock flake (diagnosed + fixed)

### 25. `kotlinWasmStoreYarnLock` — input `build/wasm/yarn.lock` missing on Windows

Consecutive Windows-only CI failures (both retrigger-fixable) with **different
root causes**:

- 2026-08-31 (roadmap docs run, attempt 1): `foojay-resolver-convention 1.0.0`
  plugin resolution failure ("was not found in any of the following sources") —
  transient Plugin Portal/resolution failure, no repo change; attempt 2 green.
- 2026-09-01 (C4 run): `:kotlinWasmStoreYarnLock` (`YarnLockStoreTask`) failed
  at input validation — `D:\a\kge\kge\build\wasm\yarn.lock` does not exist. Only
  `windows-latest` failed; ubuntu/macos green.

Evidence chain (the wasm target has **no npm dependencies**):

- Local gate artifacts: `build/wasm/package.json` is the workspaces umbrella
  with empty dependencies; the generated `build/wasm/yarn.lock` is a
  header-only 86-byte file versus the JS 98 KiB lock (webpack). The wasm store
  dir `kotlin-js-store/wasm/` is empty on macOS too.
- Failing Windows log: `kotlinWasmNpmInstall` started while `kotlinNpmInstall`
  (node pid 8360) still held Yarn's global instance mutex — "warning Waiting for
  the other yarn instance to finish (8360)" — and after that run the trivial
  wasm lock was absent, while the same build elsewhere produced it.
- Mechanism (inferred — no local Windows repro): under that contention the
  zero-dependency wasm `yarn install` can finish without writing the lock,
  consistent with the Kotlin 2.2.20+ documented behavior for wasm projects
  without npm dependencies; the store task then aborts on its required input.
  Windows timing makes the race window real; a retrigger changes the install
  ordering.

Fix: serialization in the root `build.gradle.kts` —
`tasks.matching { it.name == "kotlinWasmNpmInstall" }.configureEach { dependsOn(tasks.matching { it.name == "kotlinNpmInstall" }) }`.
The root yarn tasks are registered later in configuration, so the eager
`tasks.named` lookup fails at script evaluation ("Task with name
'kotlinWasmNpmInstall' not found"); the matching/`configureEach` form is lazy.
Verified by the `:kotlinWasmNpmInstall` dry-run ordering (install after
install) and the local full gate; Windows confirmation pending the owner push.

## 2026-09-12 — CI Windows wasm test flake (diagnosed + fixed)

### 36. `:kotlinWasmToolingSetup` — tooling dir missing `kotlin-web-helpers` on Windows

S6 enabled the browser suites in CI; the first `windows-latest` run of
`build --rerun-tasks` failed, and a rerun reproduced it exactly:

```
Execution failed for task ':kge-core:wasmJsBrowserTest'.
> Cannot find node module "kotlin-web-helpers/dist/kotlin-test-karma-runner.js"
  in "C:\Users\runneradmin\.kotlin\kotlin-npm-tooling\yarn\e49be039833f93cd8352f956b903151a"
```

- **Context.** `KotlinKarma.createTestExecutionSpec` resolves the Karma runner
  from the wasm tooling dir `~/.kotlin/kotlin-npm-tooling/<pm>/<deps-hash>`
  (`WasmNpmTooling`), not from the project `node_modules` the JS path uses. That
  dir is populated by `:kotlinWasmToolingSetup` (see KT-75714).
- **Mechanism (inferred — no local Windows repro).** With
  `org.gradle.configuration-cache=true` Gradle executes tasks in parallel even
  without `org.gradle.parallel`; verified separately on Gradle 9.7.1 (plain
  Gradle, no KGP): two independent tasks in one project run and end
  concurrently, and the Gradle manual ("Improve Performance" §5) says so
  explicitly. So the root Yarn tasks overlap, and the Windows log shows a Yarn
  mutex warning (`Waiting for the other yarn instance to finish (7840)`, pid
  7840 = `:kotlinNpmInstall`) next to `:kotlinWasmToolingSetup`, after which the
  tooling dir lacks `kotlin-web-helpers`. The warning also appears in the green
  ubuntu run, so it does not distinguish the failing run by itself; the same
  class as #25, and the #25 fix serialized only `:kotlinWasmNpmInstall`, so the
  tooling-setup task still raced.
- **Fix.** Serialize the three root Yarn tasks in `build.gradle.kts`:
  `:kotlinWasmToolingSetup` after `:kotlinNpmInstall`, then
  `:kotlinWasmNpmInstall` after `:kotlinWasmToolingSetup` (so all three are
  ordered, not just the two that were). The `:kotlin{Store,Restore}YarnLock` and
  `:kotlinWasm{Store,Restore}YarnLock` tasks are file-copy/lock-store tasks, not
  Yarn invocations, so they carry no race.
- **Evidence.** Failure deterministic across two Windows attempts (push + rerun);
  ubuntu/macos green; local full gate (`build --rerun-tasks`, browser suites
  included) green; the task graph now pulls `:kotlinNpmInstall` into
  `:kotlinWasmToolingSetup` and `:kotlinWasmToolingSetup` into
  `:kotlinWasmNpmInstall` (neither held before). Windows confirmation pending
  the owner push.
- **Upstream.** KT-81818 ("Wasm tests on Windows sometimes cannot find
  kotlin-test-karma-runner") reports the same message; it was closed as answered
  (the reporter's own fix was a Gradle/Kotest bump), so there is no upstream
  change to adopt. Note its stack resolves `kotlin-web-helpers` from the project
  dir (`build/wasm/packages/...-test`), whereas this failure resolves from the
  tooling dir — same message, not necessarily the same cause. This entry extends
  #25's diagnosis rather than superseding its Windows-confirmation item.

