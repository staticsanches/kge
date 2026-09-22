## 2026-09-20 — Phantom green: the js browser suite that runs no tests

`:kge-text-ttf:jsBrowserTest` reported **zero tests and exited 0** — alone and
inside `tools/gradle build` (chunk 10, "Gate hole"). The round confirmed the root
cause and landed the durable guard. Design material:
`docs/plans/2026-09-20-web-suite-phantom-green.md` (finding, root cause,
measurements) and `docs/plans/2026-09-20-phantom-green-guard.md` (micro-plan).
Chunk `#35`, independent of `R6` round C.

### Root cause — an async webpack entry loses the start signal

`harfbuzzjs`'s Emscripten glue is an ES module whose top level awaits the wasm
instantiation (`dist/harfbuzz.js`: `var wasmExports = await createWasm()`). That
makes the js test bundle an **async webpack entry**: the emitted entry chunk
defers its startup through `__webpack_require__.O(0, ["commons"], …)` and never
awaits it, so the script returns before the bundle registers its mocha suites.
Karma signals "all files loaded" on the window `load` event, mocha completes an
empty run, and KGP 2.4.10 writes `failOnEmptyTestSuite: false` into every
generated `karma.conf.js` (`KarmaConfig.kt:99`) — so the run exits 0.

The wasm target is immune: KGP loads it through a generated `load.mjs` that
imports the bundle and only then calls `window.__karma__.loaded()`.

### Refuted — the Karma port hypothesis

Chunk 10 suspected a Karma server-port collision between concurrent suites. That
is wrong and must not be re-opened: Karma already increments the port (9876 and
9877 observed) and the suite still degenerated with a free port, and pinning
`--remote-debugging-port=0` on the module only moved the failure between runs.
Concurrency is an **amplifier**, not the trigger — the suite ran alone at zero
tests in 2 of 5 runs and at zero in 4 of 4 concurrent runs.

### Decision — the wait belongs to the Karma config, not to `R6`

A `karma.config.d` fragment serves `kge-text-ttf/karma/await-suites.js`, which
wraps `window.__karma__.start` and starts the run only once
`window.mocha.suite.total() > 0`, or after a 10 s deadline. Rejected:

- **Reopening `R6` to drop the top-level await.** Kotlin/JS has no dynamic
  import, so the HarfBuzz facade would need a JS shim plus a suspend API — the
  shape settled in round C, reopened to fix a test-harness race.
- **Marking the entry `type: "module"`.** The emitted chunk carries no top-level
  `await`; the deferred startup lives inside webpack's runtime, so the browser
  has nothing to wait for.
- **Serializing the browser suites.** The trigger is not concurrency, so it does
  not fix the race and it costs gate time.

The wrapper forwards the arguments Karma passes to `start` (`this.config`), which
`karma-mocha` reads for `--grep`; swallowing them would be a latent divergence.
A poll cannot observe a partial registry: the bundle registers every suite in the
microtask drain that follows the wasm promise, and a `setTimeout` poll runs after
that drain. `karma-mocha`'s framework `files.unshift`s `mocha.js` and
`adapter.js`, so `__karma__.start` is assigned before the shim runs. The deadline
is the deliberate failure mode — it lets the run proceed empty so the guard fails
the build loudly instead of hanging.

### Decision — the guard covers every executed test task

`buildSrc` gains `junitXmlTestCount(resultsDir)`, and the root build script's
existing `subprojects { tasks.withType<AbstractTestTask>() }` block gains a
`doLast` that throws when it is 0. Rejected:

- **Only the browser tasks.** The `jvmTest` phantom green (entry 15) has the same
  signature — a task that executes and reports nothing — and `AbstractTestTask`
  covers all three targets with one rule.
- **Karma's `failOnEmptyTestSuite`.** Reachable only through a file the toolchain
  regenerates, and it would cover the browser suites alone.
- **The binary results instead of the JUnit XML.** `reports.junitXml.outputLocation`
  was probed and is populated at `doLast` time for both `jvmTest` and
  `jsBrowserTest`.

A task skipped for lack of sources never reaches `doLast`, so it is not failed.
`buildSrc` is a separate build: the guard does not cover its own tests, which run
through `buildSrcCheck`. `AGENTS.md`'s gate rule was corrected with it: the guard
fires on a zero, so a **short** count is still the reader's to catch.

### Verified

`tools/gradle build` green with every `build/test-results/` directory cleared
first, each suite at its count: `kge-core` 715/750/750, `kge-text-ttf`
**21/22/22**, `kge-font-roboto` 6/6/6, `kge-benchmark` 16/17 (jvm/wasmJs).
`:kge-text-ttf:jsBrowserTest` reported 22 in 10 consecutive runs, and the
three-way concurrent run reported 750/6/22. The guard's red was captured before
the shim existed: the concurrent trio failed the build on
`:kge-text-ttf:jsBrowserTest` with the zero-test message, where the same command
had exited 0.
