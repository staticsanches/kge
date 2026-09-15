package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.layer.LayerStack

/** A carrier of the engine's layer stack. */
interface HasLayers {
    /** The layers the engine composites; a stack always owns at least layer 0. */
    val layers: LayerStack
}
