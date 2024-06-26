package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.renderer.LayerDescriptor

interface LayersAddon {
    context(Window)
    fun createLayer(): Int {
        val layer = LayerDescriptor(width = screenSize.x, height = screenSize.y)
        layers.add(layer)
        return layers.size - 1
    }
}
