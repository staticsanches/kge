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

data class Float2D internal constructor(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Float2D): Float2D = (x + other.x) by (y + other.y)

    operator fun minus(other: Float2D): Float2D = (x - other.x) by (y - other.y)

    operator fun div(other: Float2D): Float2D = (x / other.x) by (y / other.y)

    operator fun div(other: Int2D): Float2D = (x / other.x) by (y / other.y)

    override fun toString(): String = "($x, $y)"
}
