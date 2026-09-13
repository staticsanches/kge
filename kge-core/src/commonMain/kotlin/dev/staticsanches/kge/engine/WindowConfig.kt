package dev.staticsanches.kge.engine

/**
 * Startup configuration for the engine window.
 *
 * [screenWidth] and [screenHeight] are logical points; [pixelWidth] and
 * [pixelHeight] are the art zoom applied to them.
 */
data class WindowConfig(
    val screenWidth: Int,
    val screenHeight: Int,
    val pixelWidth: Int = 1,
    val pixelHeight: Int = 1,
    val title: String = "",
    val resizable: Boolean = true,
    val vsync: Boolean = false,
    val fullScreen: Boolean = false,
    /**
     * Snaps the letterbox scale down to a uniform integer (at least 1) for
     * pixel-exact art; when false the scale stays fractional.
     */
    val cohesion: Boolean = false,
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
