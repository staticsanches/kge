package dev.staticsanches.kge.benchmark

import kotlin.test.Test

/**
 * Runs the shared rasterizer benchmark on the current target (JVM and JS).
 */
class RasterizerBenchmarkTest {
    @Test
    fun printBaseline() = RasterizerBenchmark.run(quick = false)
}
