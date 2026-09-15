package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.math.vector.Int2D
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A cell of the sweep: [label] names the pacing, [vsync] the config flag and
 * [highDpi] whether the high-density backing store is honored.
 */
internal data class BenchmarkMode(
    val label: String,
    val vsync: Boolean,
    val highDpi: Boolean,
)

/** The screen sizes the sweep walks, from low-res to 4K. */
internal val benchmarkSizes: List<Int2D> =
    listOf(
        Int2D(320, 240),
        Int2D(640, 360),
        Int2D(960, 540),
        Int2D(1280, 720),
        Int2D(1920, 1080),
        Int2D(2560, 1440),
        Int2D(3840, 2160),
    )

/** The JVM sweep: pacing (real vs uncapped) crossed with HiDPI on/off. */
internal val jvmModes: List<BenchmarkMode> =
    listOf(
        BenchmarkMode("vsync", vsync = true, highDpi = true),
        BenchmarkMode("vsync", vsync = true, highDpi = false),
        BenchmarkMode("uncapped", vsync = false, highDpi = true),
        BenchmarkMode("uncapped", vsync = false, highDpi = false),
    )

/** The web sweep: `requestAnimationFrame` paces the loop; HiDPI on/off crosses it. */
internal val webModes: List<BenchmarkMode> =
    listOf(
        BenchmarkMode("rAF", vsync = true, highDpi = true),
        BenchmarkMode("rAF", vsync = true, highDpi = false),
    )

/** Discarded before measuring, so JIT/GL warmup does not skew the result. */
internal val benchmarkWarmup: Duration = 2.seconds

/** The measured window per (size, mode). */
internal val benchmarkMeasure: Duration = 5.seconds
