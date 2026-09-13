package dev.staticsanches.kge.rasterizer

/**
 * A per-cell mask for a rasterized line: [shouldDrawPixel] is consulted once
 * per walked cell, in walk order.
 *
 * A stateful pattern advances on every call and is single-use: the caller owns
 * it and must not share it between lines (the rectangle/triangle composites do
 * propagate one instance across their edges). Its phase starts at the first
 * cell of the clipped walk: the clipped start for an increasing walk, the
 * clipped end for a decreasing one, and the geometric minimum for the axis
 * walks.
 */
sealed interface LinePattern {
    /** Whether the next walked cell is painted; advances stateful patterns. */
    fun shouldDrawPixel(): Boolean

    /** Paints no cell. */
    data object Empty : LinePattern {
        override fun shouldDrawPixel(): Boolean = false
    }

    /** Paints every cell. */
    data object Filled : LinePattern {
        override fun shouldDrawPixel(): Boolean = true
    }

    /** Alternates painted/skipped from the constructor's initial value (default painted). */
    class Dotted(
        private var current: Boolean = true,
    ) : LinePattern {
        override fun shouldDrawPixel(): Boolean {
            val next = current
            current = !current
            return next
        }
    }

    /** A caller-supplied pattern; the same single-use state contract applies. */
    interface Custom : LinePattern
}
