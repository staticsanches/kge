package dev.staticsanches.kge.math.vector

val IntZeroByZero: Int2D = Int2D(0, 0)
val IntOneByOne: Int2D = Int2D(1, 1)

infix fun Int.by(y: Int): Int2D =
    if (this == 0 && y == 0) {
        IntZeroByZero
    } else if (this == 1 && y == 1) {
        IntOneByOne
    } else {
        Int2D(this, y)
    }

data class Int2D internal constructor(
    val x: Int,
    val y: Int,
) {
    operator fun plus(other: Int2D): Int2D = (x + other.x) by (y + other.y)

    operator fun minus(other: Int2D): Int2D = (x - other.x) by (y - other.y)

    operator fun times(other: Int2D): Int2D = (x * other.x) by (y * other.y)

    operator fun div(other: Int2D): Int2D = (x / other.x) by (y / other.y)

    operator fun times(value: Int): Int2D = (x * value) by (y * value)

    operator fun div(value: Int): Int2D = (x / value) by (y / value)

    override fun toString(): String = "($x, $y)"
}
