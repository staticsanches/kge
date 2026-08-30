@file:Suppress("unused")

package dev.staticsanches.kge.math.vector

data class Int2D(
    val x: Int,
    val y: Int,
) {
    operator fun plus(other: Int2D): Int2D = Int2D(x + other.x, y + other.y)

    operator fun minus(other: Int2D): Int2D = Int2D(x - other.x, y - other.y)

    operator fun times(other: Int2D): Int2D = Int2D(x * other.x, y * other.y)

    operator fun div(other: Int2D): Int2D = Int2D(x / other.x, y / other.y)

    operator fun times(value: Int): Int2D = Int2D(x * value, y * value)

    operator fun div(value: Int): Int2D = Int2D(x / value, y / value)

    override fun toString(): String = "($x, $y)"

    companion object {
        val zeroByZero: Int2D = Int2D(0, 0)
        val oneByOne: Int2D = Int2D(1, 1)

        infix fun Int.by(y: Int): Int2D = Int2D(this, y)
    }
}
