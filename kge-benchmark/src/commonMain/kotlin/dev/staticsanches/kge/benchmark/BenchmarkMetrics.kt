package dev.staticsanches.kge.benchmark

/**
 * The averaged measurements of one benchmark run: [avgFps] over the measured
 * window, [minFps] the slowest distinct one-second FPS window (0 when none
 * completed) and [msPerFrame] the mean frame time.
 */
internal data class BenchmarkMetrics(
    val avgFps: Double,
    val minFps: Int,
    val msPerFrame: Double,
)
