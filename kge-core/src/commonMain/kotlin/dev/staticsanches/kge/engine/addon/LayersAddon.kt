package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasLayers

/** Creates layers on the engine's stack at the current screen size. */
interface LayersAddon : HasLayers {
    /** Appends a screen-sized layer and returns its index. */
    fun createLayer(): Int = layers.createLayer()
}
