@file:Suppress("unused")

package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.math.vector.Int2D

/**
 * Represents the visible area of the rendering process.
 */
sealed interface Viewport {
    /**
     * A [Viewport] that has a lower bound (inclusive).
     */
    interface LowerBounded : Viewport {
        val lowerBoundInclusive: Int2D
    }

    /**
     * A [Viewport] that has an upper bound (exclusive).
     */
    interface UpperBounded : Viewport {
        val upperBoundExclusive: Int2D
    }

    /**
     * A [Viewport] that is a combination of [LowerBounded] and [UpperBounded].
     */
    interface Bounded :
        LowerBounded,
        UpperBounded

    /**
     * A [Viewport] that does not have any bounds.
     */
    data object Unbounded : Viewport
}

operator fun Viewport.contains(p: Int2D): Boolean =
    when (this) {
        is Viewport.Bounded ->
            p.x >= lowerBoundInclusive.x &&
                p.y >= lowerBoundInclusive.y &&
                p.x < upperBoundExclusive.x &&
                p.y < upperBoundExclusive.y

        Viewport.Unbounded -> true
        is Viewport.LowerBounded -> p.x >= lowerBoundInclusive.x && p.y >= lowerBoundInclusive.y
        is Viewport.UpperBounded -> p.x < upperBoundExclusive.x && p.y < upperBoundExclusive.y
    }
