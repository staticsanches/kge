package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D

/**
 * The read-only dimensions of the engine window.
 *
 * [screenSize] and [pixelSize] are derived from the [config] at startup;
 * [screenSize] changes only through [Engine.setScreenSize]; [windowSize] and
 * [framebufferSize] are read from the platform each frame.
 */
class WindowInfo internal constructor(
    /** The configuration the window was created with. */
    val config: WindowConfig,
) {
    /** The logical size of the game screen, in art pixels; changed through [Engine.setScreenSize]. */
    var screenSize: Int2D = Int2D(config.screenWidth, config.screenHeight)
        internal set(value) {
            field = value
            invertedScreenSize = Float2D(1f / value.x, 1f / value.y)
        }

    /** The art zoom of [screenSize], in physical pixels per art pixel. */
    val pixelSize: Int2D = Int2D(config.pixelWidth, config.pixelHeight)

    /** The reciprocal of [screenSize], for mapping a screen point to `0..1`. */
    var invertedScreenSize: Float2D = Float2D(1f / screenSize.x, 1f / screenSize.y)
        private set

    /** The window's logical size, in points, refreshed from the platform each frame. */
    var windowSize: Int2D = Int2D.ZERO
        internal set

    /** The drawable's size, in physical pixels, refreshed from the platform each frame. */
    var framebufferSize: Int2D = Int2D.ZERO
        internal set
}
