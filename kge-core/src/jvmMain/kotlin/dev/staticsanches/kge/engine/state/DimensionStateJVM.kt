@file:Suppress("unused")

package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Int2D.Companion.by

@KGESensitiveAPI
actual fun DimensionState.recalculateViewport() {
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
