package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** The drawable rectangle the GL viewport is set to, in framebuffer pixels. */
internal data class ViewportFit(
    val position: Int2D,
    val size: Int2D,
)

/**
 * Fits the desired physical size — [screenSize] scaled by [pixelSize] — into
 * [framebufferSize], preserving its aspect ratio and centering the result.
 *
 * The scale is fractional by default; [cohesion] snaps it down to a uniform
 * integer (at least 1) for pixel-exact art. A non-positive framebuffer axis
 * yields a zero-size fit.
 */
internal fun fitViewport(
    screenSize: Int2D,
    pixelSize: Int2D,
    framebufferSize: Int2D,
    cohesion: Boolean,
): ViewportFit {
    if (framebufferSize.x <= 0 || framebufferSize.y <= 0) {
        return ViewportFit(Int2D.ZERO, Int2D.ZERO)
    }

    val desired = screenSize * pixelSize
    val scale =
        min(
            framebufferSize.x.toDouble() / desired.x,
            framebufferSize.y.toDouble() / desired.y,
        )
    val size =
        if (cohesion) {
            desired * maxOf(1, floor(scale).toInt())
        } else {
            Int2D(
                (desired.x * scale).roundToInt(),
                (desired.y * scale).roundToInt(),
            )
        }.min(framebufferSize)

    return ViewportFit((framebufferSize - size) / 2, size)
}
