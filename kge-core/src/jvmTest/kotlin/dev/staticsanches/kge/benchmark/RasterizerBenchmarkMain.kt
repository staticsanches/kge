package dev.staticsanches.kge.benchmark

/**
 * Entry point for the shared [RasterizerBenchmark] on the JVM (used by the `benchmark` Gradle task,
 * so it runs outside the CI). Pass `--quick` to reduce the number of iterations.
 */
object RasterizerBenchmarkMain {
    @JvmStatic
    fun main(args: Array<String>) {
        RasterizerBenchmark.run(quick = args.any { it == "--quick" })
    }
}
