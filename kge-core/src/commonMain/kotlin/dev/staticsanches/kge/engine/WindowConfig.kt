package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel

/**
 * Startup configuration for the engine window.
 *
 * [screenWidth] and [screenHeight] are logical points; [pixelWidth] and
 * [pixelHeight] are the art zoom applied to them. [highDpi] honors the
 * platform's high-density backing store (GLFW's macOS HiDPI framebuffer, the
 * web `devicePixelRatio`); when false the drawable matches the logical size.
 *
 * [clearColor] is the color the framebuffer is cleared to each frame before the
 * layers composite; it is visible in the letterbox area around the viewport.
 */
data class WindowConfig(
    val screenWidth: Int,
    val screenHeight: Int,
    val pixelWidth: Int = 1,
    val pixelHeight: Int = 1,
    val title: String = "",
    val resizable: Boolean = true,
    /** Keeps the initial width/height ratio fixed when the user resizes. */
    val keepAspectRatio: Boolean = false,
    val vsync: Boolean = false,
    val fullScreen: Boolean = false,
    /** Draws the platform title bar and border; borderless windows may exceed the display. */
    val decorated: Boolean = true,
    /**
     * Snaps the letterbox scale down to a uniform integer (at least 1) for
     * pixel-exact art; when false the scale stays fractional.
     */
    val cohesion: Boolean = false,
    val highDpi: Boolean = false,
    val clearColor: Pixel = Colors.BLACK,
) {
    init {
        require(screenWidth > 0 && screenHeight > 0) {
            "screen size must be positive, was ${screenWidth}x$screenHeight"
        }
        require(pixelWidth > 0 && pixelHeight > 0) {
            "pixel size must be positive, was ${pixelWidth}x$pixelHeight"
        }
    }
}
