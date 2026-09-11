package dev.staticsanches.kge.rasterizer

/**
 * A per-cell mask for a rasterized line: [shouldDrawPixel] is consulted once
 * per walked cell, in walk order, and decides whether that cell is painted.
 *
 * The instances split into stateless ([Empty], [Filled]) and stateful
 * ([Dotted], [Custom]) patterns. A stateful pattern advances its phase on
 * every call, so it is single-use: the caller owns the instance and must not
 * share it between lines (or, when propagated by the rectangle/triangle
 * composites, it deliberately continues its phase across the shared edges).
 * The pattern phase starts at the first cell of the clipped walk — the clipped
 * start for an increasing walk, the clipped end for a decreasing one, and the
 * geometric minimum for the axis walks.
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

    /**
     * Alternates painted/skipped. The first [shouldDrawPixel] returns the
     * initial value given to the constructor, which defaults to `true`
     * (painted); a [Dotted] built with `false` starts unpainted.
     */
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
