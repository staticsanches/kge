@file:Suppress("unused")

package dev.staticsanches.kge.math.vector

class MutableInt2D(
    var x: Int,
    var y: Int,
) {
    operator fun plus(other: MutableInt2D): MutableInt2D = MutableInt2D(x + other.x, y + other.y)

    operator fun plus(other: Int2D): MutableInt2D = MutableInt2D(x + other.x, y + other.y)

    operator fun plusAssign(other: MutableInt2D) {
        x += other.x
        y += other.y
    }

    operator fun plusAssign(other: Int2D) {
        x += other.x
        y += other.y
    }

    operator fun minus(other: MutableInt2D): MutableInt2D = MutableInt2D(x - other.x, y - other.y)

    operator fun minus(other: Int2D): MutableInt2D = MutableInt2D(x - other.x, y - other.y)

    operator fun minusAssign(other: MutableInt2D) {
        x -= other.x
        y -= other.y
    }

    operator fun minusAssign(other: Int2D) {
        x -= other.x
        y -= other.y
    }

    operator fun times(other: MutableInt2D): MutableInt2D = MutableInt2D(x * other.x, y * other.y)

    operator fun times(other: Int2D): MutableInt2D = MutableInt2D(x * other.x, y * other.y)

    operator fun timesAssign(other: MutableInt2D) {
        x *= other.x
        y *= other.y
    }

    operator fun timesAssign(other: Int2D) {
        x *= other.x
        y *= other.y
    }

    operator fun div(other: MutableInt2D): MutableInt2D = MutableInt2D(x / other.x, y / other.y)

    operator fun div(other: Int2D): MutableInt2D = MutableInt2D(x / other.x, y / other.y)

    operator fun divAssign(other: MutableInt2D) {
        x /= other.x
        y /= other.y
    }

    operator fun divAssign(other: Int2D) {
        x /= other.x
        y /= other.y
    }

    operator fun times(value: Int): MutableInt2D = MutableInt2D(x * value, y * value)

    operator fun timesAssign(value: Int) {
        x *= value
        y *= value
    }

    operator fun div(value: Int): MutableInt2D = MutableInt2D(x / value, y / value)

    operator fun divAssign(value: Int) {
        x /= value
        y /= value
    }

    operator fun component1(): Int = x

    operator fun component2(): Int = y

    fun toImmutable(): Int2D = Int2D(x, y)

    override fun toString(): String = "($x, $y)"

    companion object {
        infix fun Int.byMutable(y: Int): MutableInt2D = MutableInt2D(this, y)
    }
}
