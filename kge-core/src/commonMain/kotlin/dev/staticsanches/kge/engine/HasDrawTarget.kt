package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Sprite

/** A carrier of the sprite the engine draws into. */
interface HasDrawTarget {
    /**
     * The sprite the draw calls target. Assigning a non-null sprite keeps the
     * selected layer; assigning `null` selects layer 0. Must run on the engine
     * thread.
     */
    var drawTarget: Sprite?

    /**
     * Selects the layer at [index] as the draw target, marking it for upload
     * when [dirty]. An out-of-range [index] fails fast without changing the
     * selection, instead of being silently ignored. Must run on the engine
     * thread.
     */
    fun setDrawTarget(
        index: Int,
        dirty: Boolean = true,
    )
}
