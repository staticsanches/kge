package dev.staticsanches.kge.benchmark

import kotlin.time.Duration

/**
 * Accumulates the frame deltas and the published one-second FPS windows of a
 * run, discarding the initial [warmup] and then measuring for [measure].
 *
 * [sample] returns `false` once the measurement window completes, which the
 * engine loop treats as its stop condition.
 */
internal class FrameSampler(
    private val warmup: Duration,
    private val measure: Duration,
) {
    private var total = Duration.ZERO
    private var measuredFrames = 0L
    private var measuredTime = Duration.ZERO
    private var lastWindowFps = 0
    private val windowFps = mutableListOf<Int>()

    fun sample(
        elapsed: Duration,
        fps: Int,
    ): Boolean {
        if (total >= warmup) {
            measuredFrames++
            measuredTime += elapsed
            if (fps > 0 && fps != lastWindowFps) {
                windowFps += fps
                lastWindowFps = fps
            }
        }
        total += elapsed
        return total < warmup + measure
    }

    fun metrics(): BenchmarkMetrics {
        val seconds = measuredTime.inWholeMilliseconds / MILLIS_PER_SECOND
        return BenchmarkMetrics(
            avgFps = if (seconds > 0.0) measuredFrames / seconds else 0.0,
            minFps = windowFps.minOrNull() ?: 0,
            msPerFrame = if (measuredFrames > 0) measuredTime.inWholeMilliseconds.toDouble() / measuredFrames else 0.0,
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}
