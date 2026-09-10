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

