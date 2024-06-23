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

infix fun Float.mutableBy(y: Float): MutableFloat2D = MutableFloat2D(this, y)

open class Float2D internal constructor(
    open val x: Float,
    open val y: Float,
) {
    operator fun component1(): Float = x

    operator fun component2(): Float = y

    fun toMutable(): MutableFloat2D = MutableFloat2D(x, y)

    open operator fun plus(other: Float2D): Float2D = copy(x + other.x, y + other.y)

    open operator fun minus(other: Float2D): Float2D = copy(x - other.x, y - other.y)

    open operator fun div(other: Float2D): Float2D = copy(x / other.x, y / other.y)

    open operator fun div(other: Int2D): Float2D = copy(x / other.x, y / other.y)

    private fun copy(
        x: Float,
        y: Float,
    ): Float2D = if (x == this.x && y == this.y) this else Float2D(x, y)

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Float2D) return false

        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    final override fun toString(): String = "($x, $y)"

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }
}

class MutableFloat2D internal constructor(
    override var x: Float,
    override var y: Float,
) : Float2D(x, y) {
    override fun plus(other: Float2D): MutableFloat2D = MutableFloat2D(x + other.x, y + other.y)

    override fun minus(other: Float2D): MutableFloat2D = MutableFloat2D(x - other.x, y - other.y)

    override fun div(other: Float2D): MutableFloat2D = MutableFloat2D(x / other.x, y / other.y)

    override fun div(other: Int2D): MutableFloat2D = MutableFloat2D(x / other.x, y / other.y)

    infix fun swappedWith(other: MutableFloat2D) {
        x = other.x.also { other.x = x }
        y = other.y.also { other.y = y }
    }
}
