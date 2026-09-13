package dev.staticsanches.kge.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Accumulates per-frame deltas and reports the FPS of the last completed
 * one-second window.
 *
 * The FPS window follows olc: the window's frame count is published and the
 * overflow (`frameTimer -= 1.seconds`) is carried into the next window instead
 * of being reset.
 */
internal class FrameAccumulator {
    private var previous = Duration.ZERO
    private var frameTimer = Duration.ZERO
    private var windowCount = 0

    /** Frames in the last completed one-second window; `0` until one closes. */
    var fps: Int = 0
        private set

    /** Total ticks since construction. */
    var frameCount: Long = 0
        private set

    /** Returns the delta since the previous call; the first call returns [Duration.ZERO]. */
    fun tick(now: Duration): Duration {
        val elapsed = if (frameCount == 0L) Duration.ZERO else now - previous
        previous = now

        frameTimer += elapsed
        windowCount++
        frameCount++
        if (frameTimer >= 1.seconds) {
            fps = windowCount
            frameTimer -= 1.seconds
            windowCount = 0
        }
        return elapsed
    }
}
