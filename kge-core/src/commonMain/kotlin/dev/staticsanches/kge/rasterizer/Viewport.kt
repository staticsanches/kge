package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.math.vector.Int2D

/**
 * A pure integer region: the data of a clip viewport, not the algorithm. The
 * bounds match the per-pixel write check — inclusive lower, exclusive upper
 * (`x < upper.x`); `ClipService` dispatches the clipping on the variant.
 */
sealed interface Viewport {
    /** Whether ([x], [y]) lies inside this region. */
    fun contains(
        x: Int,
        y: Int,
    ): Boolean

    /** The [Int2D] form of [contains]. */
    fun contains(p: Int2D): Boolean = contains(p.x, p.y)

    /** A viewport with only a (inclusive) lower bound; no axis is capped high. */
    interface LowerBounded : Viewport {
        val lowerBoundInclusive: Int2D

        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = x >= lowerBoundInclusive.x && y >= lowerBoundInclusive.y
    }

    /** A viewport with only an (exclusive) upper bound; no axis is capped low. */
    interface UpperBounded : Viewport {
        val upperBoundExclusive: Int2D

        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = x < upperBoundExclusive.x && y < upperBoundExclusive.y
    }

    /** Both bounds: the ordinary clipped-to-surface case. */
    interface Bounded :
        LowerBounded,
        UpperBounded {
        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = super<LowerBounded>.contains(x, y) && super<UpperBounded>.contains(x, y)
    }

    /** The empty-bound variant: every point is contained. */
    data object Unbounded : Viewport {
        override fun contains(
            x: Int,
            y: Int,
        ): Boolean = true
    }
}
