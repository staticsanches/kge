package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.engine.Engine
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.math.vector.Int2D
import kotlin.time.Duration

/**
 * Runs [sizes] x [modes] sequentially, opening one engine per cell, discarding
 * [warmup] then measuring [measure], and reporting each [BenchmarkResult]
 * through [onResult] as it completes.
 */
internal suspend fun runSweep(
    sizes: List<Int2D>,
    modes: List<BenchmarkMode>,
    warmup: Duration,
    measure: Duration,
    onResult: suspend (BenchmarkResult) -> Unit,
) {
    for (size in sizes) {
        for (mode in modes) {
            val engine =
                FpsBenchmarkEngine(
                    config =
                        WindowConfig(
                            screenWidth = size.x,
                            screenHeight = size.y,
                            title = "kge-benchmark ${size.x}x${size.y} ${mode.label}",
                            vsync = mode.vsync,
                            highDpi = mode.highDpi,
                            // Borderless so a drawable larger than the display is
                            // not clamped by the window manager (macOS).
                            decorated = false,
                        ),
                    warmup = warmup,
                    measure = measure,
                )
            engine.start()
            onResult(
                BenchmarkResult(
                    size = size,
                    mode = mode.label,
                    highDpi = mode.highDpi,
                    framebufferSize = engine.frame.framebufferSize,
                    metrics = engine.metrics(),
                ),
            )
        }
    }
}

/** An [Engine] that runs for the configured window and exposes its metrics. */
private class FpsBenchmarkEngine(
    config: WindowConfig,
    warmup: Duration,
    measure: Duration,
) : Engine(config) {
    private val sampler = FrameSampler(warmup, measure)

    override suspend fun onUserUpdate(elapsed: Duration): Boolean = sampler.sample(elapsed, frame.fps)

    fun metrics(): BenchmarkMetrics = sampler.metrics()
}
