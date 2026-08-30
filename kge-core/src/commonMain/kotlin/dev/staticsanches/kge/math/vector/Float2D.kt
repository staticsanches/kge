@file:Suppress("unused")

package dev.staticsanches.kge.math.vector

data class Float2D(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Float2D): Float2D = Float2D(x + other.x, y + other.y)

    operator fun minus(other: Float2D): Float2D = Float2D(x - other.x, y - other.y)

    operator fun div(other: Float2D): Float2D = Float2D(x / other.x, y / other.y)

    operator fun div(other: Int2D): Float2D = Float2D(x / other.x, y / other.y)

    override fun toString(): String = "($x, $y)"

    companion object {
        val zeroByZero: Float2D = Float2D(0f, 0f)
        val oneByOne: Float2D = Float2D(1f, 1f)

        infix fun Float.by(y: Float): Float2D = Float2D(this, y)
    }
}
