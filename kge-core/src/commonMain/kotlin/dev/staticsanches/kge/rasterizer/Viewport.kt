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

fun Viewport.fittestX(p: Int2D): Int = fittestX(p.x)

fun Viewport.fittestX(x: Int): Int =
    when (this) {
        is Viewport.Bounded -> {
            if (x < lowerBoundInclusive.x) {
                lowerBoundInclusive.x
            } else if (x >= upperBoundExclusive.x) {
                upperBoundExclusive.x - 1
            } else {
                x
            }
        }

        Viewport.Unbounded -> x
        is Viewport.LowerBounded -> if (x < lowerBoundInclusive.x) lowerBoundInclusive.x else x
        is Viewport.UpperBounded -> if (x >= upperBoundExclusive.x) upperBoundExclusive.x - 1 else x
    }

fun Viewport.fittestY(p: Int2D): Int = fittestY(p.y)

fun Viewport.fittestY(y: Int): Int =
    when (this) {
        is Viewport.Bounded ->
            if (y < lowerBoundInclusive.y) {
                lowerBoundInclusive.y
            } else if (y >= upperBoundExclusive.y) {
                upperBoundExclusive.y - 1
            } else {
                y
            }

        Viewport.Unbounded -> y
        is Viewport.LowerBounded -> if (y < lowerBoundInclusive.y) lowerBoundInclusive.y else y
        is Viewport.UpperBounded -> if (y >= upperBoundExclusive.y) upperBoundExclusive.y - 1 else y
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
