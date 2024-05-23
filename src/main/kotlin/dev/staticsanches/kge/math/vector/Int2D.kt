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

infix fun Int.mutableBy(y: Int): MutableInt2D = MutableInt2D(this, y)

open class Int2D internal constructor(open val x: Int, open val y: Int) {
    operator fun component1(): Int = x

    operator fun component2(): Int = y

    fun toMutable(): MutableInt2D = MutableInt2D(x, y)

    open operator fun plus(other: Int2D): Int2D = copy(x + other.x, y + other.y)

    open operator fun minus(other: Int2D): Int2D = copy(x - other.x, y - other.y)

    open operator fun times(other: Int2D): Int2D = copy(x * other.x, y * other.y)

    open operator fun div(other: Int2D): Int2D = copy(x / other.x, y / other.y)

    open operator fun times(value: Int): Int2D = copy(x * value, y * value)

    open operator fun div(value: Int): Int2D = copy(x / value, y / value)

    private fun copy(
        x: Int,
        y: Int,
    ): Int2D = if (x == this.x && y == this.y) this else Int2D(x, y)

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Int2D) return false

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    final override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    final override fun toString(): String = "($x, $y)"
}

class MutableInt2D internal constructor(override var x: Int, override var y: Int) : Int2D(x, y) {
    override fun plus(other: Int2D): MutableInt2D = MutableInt2D(x + other.x, y + other.y)

    override fun minus(other: Int2D): MutableInt2D = MutableInt2D(x - other.x, y - other.y)

    override fun times(other: Int2D): MutableInt2D = MutableInt2D(x * other.x, y * other.y)

    override fun div(other: Int2D): MutableInt2D = MutableInt2D(x / other.x, y / other.y)

    override fun times(value: Int): MutableInt2D = MutableInt2D(x * value, y * value)

    override fun div(value: Int): MutableInt2D = MutableInt2D(x / value, y / value)

    infix fun swappedWith(other: MutableInt2D) {
        x = other.x.also { other.x = x }
        y = other.y.also { other.y = y }
    }
}
