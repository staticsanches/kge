package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.math.vector.Int2D

/**
 * One measured cell of the sweep: the [size] requested (logical), the [mode]
 * and [highDpi] flags, the resulting physical [framebufferSize] and the
 * [metrics].
 */
internal data class BenchmarkResult(
    val size: Int2D,
    val mode: String,
    val highDpi: Boolean,
    val framebufferSize: Int2D,
    val metrics: BenchmarkMetrics,
)
