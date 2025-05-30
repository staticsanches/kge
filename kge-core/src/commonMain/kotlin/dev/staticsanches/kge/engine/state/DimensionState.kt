package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D

class DimensionState {
    /**
     * Logical size of the game screen.
     */
    var screenSize: Int2D = Int2D.zeroByZero
        @KGESensitiveAPI
        set(value) {
            check(value.x > 0 && value.y > 0) { "Invalid screen size $value" }
            field = value
            invertedScreenSize = Float2D.oneByOne / value
        }

    var invertedScreenSize: Float2D = Float2D.zeroByZero
        private set

    /**
     * Pixel scale to convert from screen size to window size.
     * In JVM, the initial [windowSize] is equal to [screenSize] * [pixelSize].
     * In JS, the initial [screenSize] is equal to [windowSize] / [pixelSize].
     */
    var pixelSize: Int2D = Int2D.zeroByZero
        @KGESensitiveAPI
        set(value) {
            check(value.x > 0 && value.y > 0) { "Invalid pixel size $value" }
            field = value
        }

    /**
     * The size of the rendered window in screen units.
     */
    var windowSize: Int2D = Int2D.zeroByZero
        @KGESensitiveAPI
        set(value) {
            check(value.x > 0 && value.y > 0) { "Invalid window size $value" }
            field = value
        }

    /**
     * Pixel scale to convert from screen units to pixels.
     * Usually 1x1, but in JVM can be different depending on the monitor.
     */
    var windowPixelSize: Int2D = Int2D.oneByOne
        @KGESensitiveAPI
        set(value) {
            check(value.x > 0 && value.y > 0) { "Invalid window pixel size $value" }
            field = value
        }

    /**
     * The size of the rendered window in pixels. [windowSizeInPixels] = [windowPixelSize] * [windowSize].
     */
    var windowSizeInPixels: Int2D = Int2D.zeroByZero
        @KGESensitiveAPI
        set(value) {
            check(value.x > 0 && value.y > 0) { "Invalid window size in pixels $value" }
            field = value
        }

    /**
     * The viewport size in pixels.
     */
    var viewportSize: Int2D = Int2D.zeroByZero
        @KGESensitiveAPI
        set(value) {
            check(value.x > 0 && value.y > 0) { "Invalid viewport size $value" }
            field = value
        }

    /**
     * The view port position in pixels.
     */
    var viewportPosition: Int2D = Int2D.zeroByZero
        @KGESensitiveAPI set
}

@KGESensitiveAPI
expect fun DimensionState.recalculateViewport()
