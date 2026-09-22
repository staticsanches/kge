# Phantom green — micro-plan: Karma-side wait + durable zero-test guard

**Date:** 2026-09-20. Closes `docs/plans/2026-09-20-web-suite-phantom-green.md`
(root cause there). Owner decisions at the touch-point: wait for suite
registration in the `kge-text-ttf` Karma config instead of reopening `R6`; the
guard covers **every** executed `AbstractTestTask`; the guard's logic lives in
`buildSrc` with unit tests and the root script only wires it.

## Contract

1. A test task that executes and reports zero tests **fails the build**.
2. `:kge-text-ttf:jsBrowserTest` reports its 22 tests on every run, alone and
   under concurrency.

## Resolved decisions — do not re-litigate

- **No Karma server-port override.** Karma already increments its port; the port
  was never the cause (see the finding's measurements).
- **No `type: "module"` on the bundle entry.** The emitted chunk has no top-level
  `await`; the deferred startup is inside webpack's runtime.
- **Do not reopen `R6`.** Kotlin/JS has no dynamic import, so removing the
  top-level await would need a JS shim plus a suspend API, changing the shape
  settled in round C.
- **Do not serialize the browser suites.** It does not fix the race — the suite
  also zeroes when it runs alone — and it costs gate time.
- The guard is a `doLast` on `AbstractTestTask` reading
  `reports.junitXml.outputLocation`. That location was probed: it exists and is
  populated at `doLast` time for both `jvmTest` and `jsBrowserTest`.
- A task skipped for lack of sources never reaches `doLast`; accepted.
- `buildSrc` is a separate build, so `subprojects { }` does not reach its own
  tests — out of scope.
- The tdd-developer never commits, never stages, and never edits this plan.

## Step 1 — count tests in JUnit XML (buildSrc, strict TDD)

New `buildSrc/src/main/kotlin/TestResults.kt`:

```kotlin
/** Sums the `tests` attribute of the JUnit XML files directly inside [resultsDir]. */
fun junitXmlTestCount(resultsDir: File): Int
```

New `buildSrc/src/test/kotlin/TestResultsTest.kt` (`kotlin.test`, matching
`EmbeddedResourcesTest` style). Red first: the function does not exist. Cases:

1. missing directory → 0
2. empty directory → 0
3. directory holding only non-XML files → 0
4. one XML with `tests="22"` → 22
5. two XMLs carrying 20 and 2 → 22
6. an XML reporting `tests="0"` → 0
7. an XML with no `tests` attribute → 0
8. an XML in a subdirectory is not counted (Gradle writes `binary/` beside them)

Command: `tools/gradle -p buildSrc test` — red, then green.

## Step 2 — wire the guard (root build script)

In the existing `subprojects { tasks.withType<AbstractTestTask>().configureEach { … } }`
block of `build.gradle.kts`, add a `doLast` that resolves
`reports.junitXml.outputLocation.get().asFile`, calls `junitXmlTestCount`, and
throws `GradleException` when the count is 0. The message names the task path and
the results directory and reads on its own, without referencing the docs tree.

**Red evidence (required, before step 3):** with the race still present, run

```bash
tools/gradle :kge-core:jsBrowserTest :kge-font-roboto:jsBrowserTest :kge-text-ttf:jsBrowserTest --max-workers=3
```

The `kge-text-ttf` suite reports zero and the build must **fail** with the
guard's message; the same command exited 0 before this step. Capture the failing
output.

## Step 3 — hold the run until the suites register (`kge-text-ttf`)

New `kge-text-ttf/karma.config.d/await-suites.js` — server-side fragment that
pushes the browser script into `config.files`, resolving the module directory
from `config.basePath` (both `build/js/packages/…` and `build/wasm/packages/…`
sit four levels below the repository root).

New `kge-text-ttf/karma/await-suites.js` — browser-side shim wrapping
`window.__karma__.start` so the run begins only once
`window.mocha.suite.total()` is non-zero, or a 10 s deadline expires (the Gradle
guard then fails the build instead of passing silently).

- Wrapping at load time is safe: `karma-mocha`'s framework `files.unshift`s
  `mocha.js` and `adapter.js`, so `__karma__.start` is assigned before this file
  runs.
- Polling cannot see a partial registry: the bundle registers every suite in the
  microtask drain that follows the wasm promise, and a `setTimeout` poll runs
  after that drain.
- `karma.config.d` is shared by the js and wasm test tasks of the module. The
  shim is a no-op on the wasm path, which already signals after its suites
  register; that is accepted rather than conditioned away.

## Success criteria

- `tools/gradle -p buildSrc test` green over the step 1 cases.
- `tools/gradle :kge-text-ttf:jsBrowserTest` reports **22 tests in 10 consecutive
  runs** (before the fix: two zeros in five).
- `tools/gradle :kge-core:jsBrowserTest :kge-font-roboto:jsBrowserTest :kge-text-ttf:jsBrowserTest --max-workers=3`
  reports 750 / 6 / 22.
- `tools/gradle :kge-text-ttf:wasmJsBrowserTest` still reports 22.
- `tools/gradle build` green **three times in a row**, every suite at its count.
- The step 2 red evidence is captured.
