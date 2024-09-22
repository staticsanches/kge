@file:Suppress("unused")

package dev.staticsanches.kge.math.vector

val FloatZeroByZero: Float2D = Float2D(0f, 0f)
val FloatOneByOne: Float2D = Float2D(1f, 1f)

infix fun Float.by(y: Float): Float2D =
    if (this == 0f && y == 0f) {
        FloatZeroByZero
    } else if (this == 1f && y == 1f) {
        FloatOneByOne
    } else {
        Float2D(this, y)
    }

class Float2D internal constructor(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Float2D): Float2D = (x + other.x) by (y + other.y)

    operator fun minus(other: Float2D): Float2D = (x - other.x) by (y - other.y)

    operator fun div(other: Float2D): Float2D = (x / other.x) by (y / other.y)

    operator fun div(other: Int2D): Float2D = (x / other.x) by (y / other.y)

    operator fun component1(): Float = x

    operator fun component2(): Float = y

    override fun toString(): String = "($x, $y)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Float2D

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }
}
