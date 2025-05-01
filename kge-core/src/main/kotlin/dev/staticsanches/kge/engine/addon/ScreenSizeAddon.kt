package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer

interface ScreenSizeAddon : WindowDependentAddon {
    fun changeScreenSize(newScreenSize: Int2D) {
        dimensionState.screenSize = newScreenSize
        dimensionState.updateViewport()

        val (x, y) = newScreenSize
        layers.forEach { it.resize(x, y) }
        drawTarget = null

        Renderer.clearBuffer(Colors.BLACK, true)
        Renderer.displayFrame()
        Renderer.clearBuffer(Colors.BLACK, true)
        Renderer.updateViewport(dimensionState.viewportPosition, dimensionState.viewportSize)
    }
}
