package dev.staticsanches.kge.math.vector

@JvmInline
value class Int2D private constructor(
    private val xy: Long,
) {
    constructor(x: Int, y: Int) : this((x.toLong() shl 32) or (y.toLong() and 0xffffffffL))

    val x: Int
        get() = (xy shr 32).toInt()
    val y: Int
        get() = xy.toInt()

    operator fun plus(other: Int2D): Int2D = Int2D(x + other.x, y + other.y)

    operator fun minus(other: Int2D): Int2D = Int2D(x - other.x, y - other.y)

    operator fun times(other: Int2D): Int2D = Int2D(x * other.x, y * other.y)

    operator fun div(other: Int2D): Int2D = Int2D(x / other.x, y / other.y)

    operator fun times(value: Int): Int2D = Int2D(x * value, y * value)

    operator fun div(value: Int): Int2D = Int2D(x / value, y / value)

    operator fun component1(): Int = x

    operator fun component2(): Int = y

    override fun toString(): String = "($x, $y)"

    companion object {
        val zeroByZero: Int2D = Int2D(0L)

        infix fun Int.by(y: Int): Int2D = Int2D(this, y)
    }
}
