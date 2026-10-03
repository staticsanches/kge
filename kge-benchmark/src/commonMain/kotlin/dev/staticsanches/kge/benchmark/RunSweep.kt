package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.math.vector.Int2D
import kotlin.time.Duration

/**
 * Runs [sizes] x [modes] x [workloads] sequentially, opening one engine per
 * cell, discarding [warmup] then measuring [measure], reporting the first
 * cell's driver texture limit through [onCapabilities] and each [BenchmarkResult]
 * through [onResult] as it completes.
 */
internal suspend fun runSweep(
    sizes: List<Int2D>,
    modes: List<BenchmarkMode>,
    workloads: List<BenchmarkWorkload>,
    warmup: Duration,
    measure: Duration,
    onCapabilities: suspend (maxTextureSize: Int) -> Unit = {},
    onResult: suspend (BenchmarkResult) -> Unit,
) {
    var capabilitiesReported = false
    for (size in sizes) {
        for (mode in modes) {
            for (workload in workloads) {
                val engine =
                    FpsBenchmarkEngine(
                        config =
                            WindowConfig(
                                screenWidth = size.x,
                                screenHeight = size.y,
                                title = "kge-benchmark ${size.x}x${size.y} ${mode.label} ${workload.label}",
                                vsync = mode.vsync,
                                highDpi = mode.highDpi,
                                // Borderless so a drawable larger than the display is
                                // not clamped by the window manager (macOS).
                                decorated = false,
                            ),
                        warmup = warmup,
                        measure = measure,
                        workload = workload,
                    )
                engine.start()
                if (!capabilitiesReported) {
                    capabilitiesReported = true
                    onCapabilities(engine.maxTextureSize)
                }
                onResult(
                    BenchmarkResult(
                        size = size,
                        mode = mode.label,
                        workload = workload.label,
                        highDpi = mode.highDpi,
                        framebufferSize = engine.frame.framebufferSize,
                        metrics = engine.metrics(),
                    ),
                )
            }
        }
    }
}
