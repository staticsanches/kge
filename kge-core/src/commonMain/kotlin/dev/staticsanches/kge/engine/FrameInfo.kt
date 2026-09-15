package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import kotlin.time.Duration

/**
 * A read-only snapshot of the loop for one frame.
 *
 * [elapsed] is the frame delta, [fps] the frame count of the last completed
 * one-second window (`0` until one completes), and [frameCount] the total frames
 * rendered since the engine was created. [framebufferSize] is the drawable size
 * of that frame, in physical pixels.
 */
data class FrameInfo(
    val elapsed: Duration,
    val fps: Int,
    val frameCount: Long,
    val framebufferSize: Int2D,
)
