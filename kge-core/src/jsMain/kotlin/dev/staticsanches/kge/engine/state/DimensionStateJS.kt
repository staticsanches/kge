@file:Suppress("unused")

package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by

@KGESensitiveAPI
actual fun DimensionState.recalculateViewport() {
    // https://webglfundamentals.org/webgl/lessons/webgl-resizing-the-canvas.html
    windowPixelSize = Int2D.oneByOne
    windowSizeInPixels = windowSize

    screenSize = windowSize / pixelSize // updates the screen size

    val desiredWindowSize = screenSize * pixelSize

    var x = windowSize.x
    var y = (x * desiredWindowSize.y) / desiredWindowSize.x
    if (y > windowSize.y) {
        y = windowSize.y
        x = (y * desiredWindowSize.x) / desiredWindowSize.y
    }

    viewportSize = x by y
    viewportPosition = (windowSizeInPixels - viewportSize) / 2
}
