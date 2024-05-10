@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "unused")

package dev.staticsanches.kge.types.vector


typealias Int2D = Vector2D<Int>
typealias MutableInt2D = MutableVector2D<Int>

fun Int2D(x: Int, y: Int): Int2D = x by y
fun MutableInt2D(x: Int, y: Int): MutableInt2D = IntMutableVector2D(x, y)

val IntZeroByZero: Int2D = IntVector2D(0, 0)
val IntOneByOne: Int2D = IntVector2D(1, 1)

infix fun Int.by(y: Int): Int2D =
	if (this == 0 && y == 0) IntZeroByZero
	else if (this == 1 && y == 1) IntOneByOne
	else IntVector2D(this, y)

infix fun Int.mutableBy(y: Int): MutableInt2D = IntMutableVector2D(this, y)

private abstract class BaseIntVector2D : Int2D {

	override fun Int.plus(other: Int): Int = this + other
	override fun Int.minus(other: Int): Int = this - other
	override fun Int.times(other: Int): Int = this * other
	override fun Int.times(other: Double): Double = this * other
	override fun Int.div(other: Int): Int = this / other
	override fun Int.unaryMinus(): Int = +this
	override fun Int.floor(): Int = this
	override fun Int.ceil(): Int = this
	override fun Int.toDouble(): Double = this.toDouble()
	override fun compare(a: Int, b: Int): Int = a.compareTo(b)

	override fun toString(): String = "($x, $y)"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Vector2D<*>) return false

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

private class IntVector2D(override val x: Int, override val y: Int) : BaseIntVector2D(), Int2D {

	override fun newInstance(newX: Int, newY: Int): Int2D = newX by newY
	override fun toMutable(): MutableInt2D = x mutableBy y
	override fun toDouble2D(): Double2D = x.toDouble() by y.toDouble()
	override fun toFloat2D(): Float2D = x.toFloat() by y.toFloat()
	override fun toInt2D(): Int2D = this

}

private class IntMutableVector2D(override var x: Int, override var y: Int) :
	BaseIntVector2D(), MutableInt2D {

	override fun newInstance(newX: Int, newY: Int): MutableInt2D = newX mutableBy newY
	override fun toImmutable(): Int2D = x by y
	override fun toDouble2D(): MutableDouble2D = x.toDouble() mutableBy y.toDouble()
	override fun toFloat2D(): MutableFloat2D = x.toFloat() mutableBy y.toFloat()
	override fun toInt2D(): MutableInt2D = copy()

}
