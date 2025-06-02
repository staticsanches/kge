package dev.staticsanches.kge.engine.state.input

import dev.staticsanches.kge.annotations.KGESensitiveAPI

/**
 * Stores the keyboard modifiers captured by the keyboard callback.
 */
expect value class KeyboardModifiers
    @KGESensitiveAPI
    constructor(
        private val mods: Int,
    )
