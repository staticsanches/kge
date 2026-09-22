# Web browser suites: a suite can run zero tests and exit 0

**Date:** 2026-09-20. Status: **root cause confirmed; fix planned** in
`docs/plans/2026-09-20-phantom-green-guard.md`. The operational rule that came out
of the original finding is in `AGENTS.md` ("Gate"); the original verified facts
are in `docs/decisions/phase-1/10-gate-surface-c5.md` ("Gate hole", 2026-09-20).

## Symptom

`tools/gradle build` finishes `BUILD SUCCESSFUL` while
`:kge-text-ttf:jsBrowserTest` runs **zero tests**. The suite's Karma server
starts, ChromeHeadless is launched, and nothing is reported.

## Evidence

With every `build/test-results/` directory deleted first, one gate run reported:

| suite | tests |
|---|---|
| `kge-core` jvm / js / wasmJs | 715 / 750 / 750 |
| `kge-text-ttf` jvm / js / wasmJs | 21 / **0** / 22 |
| `kge-font-roboto` jvm / js / wasmJs | 6 / 6 / 6 |
| `kge-benchmark` jvm / wasmJs | 16 / 17 |

Every suite but one reported its full count, and the build exited 0.

- `kge-text-ttf/build/test-results/jsBrowserTest/`: no XML at all, and
  `binary/results-generic.bin` at 44 bytes — against nine XMLs and 6392 bytes for
  the same suite on `wasmJsBrowserTest`.
- `tools/gradle :kge-text-ttf:jsBrowserTest --rerun-tasks`: the real run, 22
  tests, nine XMLs, 6260 bytes, 0 failures.
- `tools/gradle :kge-text-ttf:jsBrowserTest` alone, right after a forced run:
  also the real 22 — so the empty shape is not merely stale state. Later
  re-measured: running alone it also degenerates, in 2 of 5 runs (see
  Measurements), so "alone is safe" was a single lucky sample.
- `kge-core`'s js suite never degenerated: 750 both inside `build` and alone.
- The build log shows `Karma v6.4.4 server started at http://localhost:9876/`
  and `Launching browsers ChromeHeadlessNoSandbox`, then no test output.

## Root cause (confirmed 2026-09-20)

`harfbuzzjs` ships its Emscripten glue as an ES module whose top level awaits the
wasm instantiation (`dist/harfbuzz.js`: `var wasmExports = await createWasm()`).
That makes the `kge-text-ttf` js test bundle an async webpack entry: the emitted
entry chunk defers its startup through `__webpack_require__.O(0, ["commons"], …)`
and never awaits it, so the script returns before the bundle registers its mocha
suites. Karma signals "all files loaded" on the window `load` event, mocha runs an
empty suite, and KGP 2.4.10 writes `failOnEmptyTestSuite: false` into every
generated `karma.conf.js` (`KarmaConfig.kt:99`) — so the run exits 0.

The emitted chunk carries no top-level `await`, so loading it as `type: "module"`
cannot help: the deferred startup lives inside webpack's runtime.

- The wasm target is immune — KGP loads it through a generated `load.mjs` that
  imports the bundle and only then calls `window.__karma__.loaded()`.
- **The port hypothesis is refuted.** Karma already increments its server port
  (9876, 9877 observed) and the suite still degenerated; pinning
  `--remote-debugging-port=0` on the module only moved the failure around. The
  earlier "fix" of that shape was a coincidence.
- Concurrency is an **amplifier**, not the trigger.

## Measurements (2026-09-20)

| command | `kge-text-ttf:jsBrowserTest` |
|---|---|
| `:kge-text-ttf:jsBrowserTest` alone, 5 runs | 22, 22, **0**, **0**, 22 |
| `:kge-core:` + `:kge-font-roboto:` + `:kge-text-ttf:jsBrowserTest`, `--max-workers=3` | **0** (core 750, roboto 6) |
| the same pair, `--max-workers=2` | **0** (roboto 6) |

The zeroing run's own log ends 109 ms after the HarfBuzz wasm is served, with no
suite ever registered and no browser-side error.

## Fix

`docs/plans/2026-09-20-phantom-green-guard.md`: a browser-side Karma shim holds
the run until the suites register, and a Gradle-level guard fails any executed
test task that reports zero tests — the durable half, which does not depend on
this root cause.

## Why it matters

This is the second phantom-green mechanism in the project. The first — `jvmTest`
running with the kotest engine never started (decisions log item 15) — was fixed
by disabling test-task up-to-dateness, which does not help here: the task *does*
execute, and it is the suite that reports nothing.
