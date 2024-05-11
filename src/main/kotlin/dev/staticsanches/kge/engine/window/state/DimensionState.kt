package dev.staticsanches.kge.engine.window.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.types.vector.*


class DimensionState {

	/**
	 * Logical size of the game screen.
	 */
	var screenSize: Int2D = IntZeroByZero
		@KGESensitiveAPI set(value) {
			check(value.x > 0 && value.y > 0) { "Invalid screen size $value" }
			field = value
			invertedScreenSize = FloatOneByOne / value
		}

	var invertedScreenSize: Float2D = FloatZeroByZero
		private set

	/**
	 * Pixel scale to convert from screen size to window size.
	 * The initial [windowSize] is equal to [screenSize] * [pixelSize].
	 */
	var pixelSize: Int2D = IntZeroByZero
		@KGESensitiveAPI set(value) {
			check(value.x > 0 && value.y > 0) { "Invalid pixel size $value" }
			field = value
		}

	/**
	 * The size of the rendered window in screen units.
	 */
	var windowSize: Int2D = IntZeroByZero
		@KGESensitiveAPI set(value) {
			check(value.x > 0 && value.y > 0) { "Invalid window size $value" }
			field = value
		}

	/**
	 * Pixel scale to convert from screen units to pixels. Usually 1x1, but can be different depending on the monitor.
	 */
	var windowPixelSize: Int2D = IntZeroByZero
		@KGESensitiveAPI set(value) {
			check(value.x > 0 && value.y > 0) { "Invalid window pixel size $value" }
			field = value
		}

	/**
	 * The size of the rendered window in pixels. [windowSizeInPixels] = [windowPixelSize] * [windowSize].
	 */
	var windowSizeInPixels: Int2D = IntZeroByZero
		@KGESensitiveAPI set(value) {
			check(value.x > 0 && value.y > 0) { "Invalid window size in pixels $value" }
			field = value
		}

	/**
	 * The viewport size in pixels.
	 */
	var viewportSize: Int2D = IntZeroByZero
		@KGESensitiveAPI set(value) {
			check(value.x > 0 && value.y > 0) { "Invalid viewport size $value" }
			field = value
		}

	/**
	 * The view port position in pixels.
	 */
	var viewportPosition: Int2D = IntZeroByZero
		@KGESensitiveAPI set

	@KGESensitiveAPI
	fun updateViewport() {
		val desiredWindowSize = screenSize * pixelSize

		var x = windowSize.x
		var y = (x * desiredWindowSize.y) / desiredWindowSize.x
		if (y > windowSize.y) {
			y = windowSize.y
			x = (y * desiredWindowSize.x) / desiredWindowSize.y
		}

		viewportSize = (x by y) * windowPixelSize

		viewportPosition = (windowSizeInPixels - viewportSize) / 2
	}

}
