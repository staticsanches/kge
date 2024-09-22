package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.renderer.LayerDescriptor

interface LayersAddon : WindowDependentAddon {
    fun createLayer(): Int {
        val layer = LayerDescriptor(window = window, width = screenSize.x, height = screenSize.y)
        layers.add(layer)
        return layers.size - 1
    }
}
