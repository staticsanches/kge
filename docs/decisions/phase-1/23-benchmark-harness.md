## 2026-09-13 — Benchmark harness: `kotlinx-benchmark` for future benchmarks

Owner decision: any future performance measurement uses the
`kotlinx-benchmark` Gradle plugin + runtime
(`org.jetbrains.kotlinx.benchmark`) instead of ad-hoc `System.nanoTime()`
loops. On the JVM it generates and runs JMH; the harness supplies warmup, JVM
forking, isolation and blackholes (no dead-code elimination), and per-target
runners exist for js/wasmJs/native.

Rationale: the C6 native-bulk spike (`12-raster-c6.md`) is the recorded
counter-example — the first pass had no warmup, the JIT was not settled and the
probe could loop ~2^31 times; even the corrected throwaway harness was
time-boxed and indicative only. The benchmark-gated items still open
(`multiDrawArrays` batching, carry-forward #22; the web `Long`/`Duration`
hot-path constraint) need reproducible numbers, and the roadmap already lists
the harness as optional.

Scope: adopt it just-in-time, when a benchmark actually gates a decision — not
wired into the build now, and a separate source set (not `commonTest`, which
pins behavior). JVM/JMH is the mature path; the web runners are less isolated,
so web numbers carry a lower-confidence note. This supersedes the
throwaway-spike method, not any recorded result.
