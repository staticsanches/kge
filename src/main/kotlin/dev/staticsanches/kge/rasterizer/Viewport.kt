package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.types.vector.Int2D

/**
 * Represents the visible area of the rendering process.
 */
sealed interface Viewport {
    /**
     * A [Viewport] that has a lower bound (inclusive).
     */
    interface LowerBounded : Viewport {
        val lowerBoundInclusive: Int2D

        override fun fittestX(x: Int): Int = if (x < lowerBoundInclusive.x) lowerBoundInclusive.x else x

        override fun fittestY(y: Int): Int = if (y < lowerBoundInclusive.y) lowerBoundInclusive.y else y

        override fun outCode(p: Int2D): CohenSutherlandOutCode {
            var outCode = CohenSutherlandOutCode.INSIDE
            if (p.x < lowerBoundInclusive.x) outCode = outCode or CohenSutherlandOutCode.LEFT
            if (p.y < lowerBoundInclusive.y) outCode = outCode or CohenSutherlandOutCode.TOP
            return outCode
        }

        override fun contains(p: Int2D): Boolean = p.x >= lowerBoundInclusive.x && p.y >= lowerBoundInclusive.y
    }

    /**
     * A [Viewport] that has an upper bound (exclusive).
     */
    interface UpperBounded : Viewport {
        val upperBoundExclusive: Int2D

        override fun fittestX(x: Int): Int = if (x >= upperBoundExclusive.x) upperBoundExclusive.x - 1 else x

        override fun fittestY(y: Int): Int = if (y >= upperBoundExclusive.y) upperBoundExclusive.y - 1 else y

        override fun outCode(p: Int2D): CohenSutherlandOutCode {
            var outCode = CohenSutherlandOutCode.INSIDE
            if (p.x >= upperBoundExclusive.x) outCode = outCode or CohenSutherlandOutCode.RIGHT
            if (p.y >= upperBoundExclusive.y) outCode = outCode or CohenSutherlandOutCode.BOTTOM
            return outCode
        }

        override fun contains(p: Int2D): Boolean = p.x < upperBoundExclusive.x && p.y < upperBoundExclusive.y
    }

    /**
     * A [Viewport] that is a combination of [LowerBounded] and [UpperBounded].
     */
    interface Bounded : LowerBounded, UpperBounded {
        override fun fittestX(x: Int): Int =
            when {
                x < lowerBoundInclusive.x -> lowerBoundInclusive.x
                x >= upperBoundExclusive.x -> upperBoundExclusive.x - 1
                else -> x
            }

        override fun fittestY(y: Int): Int =
            when {
                y < lowerBoundInclusive.y -> lowerBoundInclusive.y
                y >= upperBoundExclusive.y -> upperBoundExclusive.y - 1
                else -> y
            }

        override fun outCode(p: Int2D): CohenSutherlandOutCode =
            super<LowerBounded>.outCode(p) or super<UpperBounded>.outCode(p)

        override fun contains(p: Int2D): Boolean = super<LowerBounded>.contains(p) && super<UpperBounded>.contains(p)
    }

    /**
     * A [Viewport] that does not have any bounds.
     */
    data object Unbounded : Viewport {
        override fun fittestX(x: Int): Int = x

        override fun fittestY(y: Int): Int = y

        override fun outCode(p: Int2D): CohenSutherlandOutCode = CohenSutherlandOutCode.INSIDE

        override fun contains(p: Int2D): Boolean = true
    }

    fun fittestX(x: Int): Int

    fun fittestX(p: Int2D): Int = fittestX(p.x)

    fun fittestY(y: Int): Int

    fun fittestY(p: Int2D): Int = fittestY(p.y)

    fun outCode(p: Int2D): CohenSutherlandOutCode

    operator fun contains(p: Int2D): Boolean
}

/**
 * Out code from [Cohen-Sutherland clip algorithm](https://en.wikipedia.org/wiki/Cohen–Sutherland_algorithm).
 */
@JvmInline
value class CohenSutherlandOutCode(val value: Int) {
    infix fun or(other: CohenSutherlandOutCode): CohenSutherlandOutCode = CohenSutherlandOutCode(value or other.value)

    infix fun and(other: CohenSutherlandOutCode): CohenSutherlandOutCode = CohenSutherlandOutCode(value and other.value)

    companion object {
        val INSIDE = CohenSutherlandOutCode(0b0000)

        val LEFT = CohenSutherlandOutCode(0b0001)
        val RIGHT = CohenSutherlandOutCode(0b0010)
        val BOTTOM = CohenSutherlandOutCode(0b0100)
        val TOP = CohenSutherlandOutCode(0b1000)
    }
}
