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

class Int2D internal constructor(
    val x: Int,
    val y: Int,
) {
    operator fun plus(other: Int2D): Int2D = (x + other.x) by (y + other.y)

    operator fun minus(other: Int2D): Int2D = (x - other.x) by (y - other.y)

    operator fun times(other: Int2D): Int2D = (x * other.x) by (y * other.y)

    operator fun div(other: Int2D): Int2D = (x / other.x) by (y / other.y)

    operator fun times(value: Int): Int2D = (x * value) by (y * value)

    operator fun div(value: Int): Int2D = (x / value) by (y / value)

    operator fun component1(): Int = x

    operator fun component2(): Int = y

    override fun toString(): String = "($x, $y)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Int2D

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }
}
