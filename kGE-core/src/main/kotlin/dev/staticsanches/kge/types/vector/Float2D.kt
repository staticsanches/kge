@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "unused")

package dev.staticsanches.kge.types.vector


typealias Float2D = Vector2D<Float>
typealias MutableFloat2D = MutableVector2D<Float>

fun Float2D(x: Float, y: Float): Float2D = x by y
fun MutableFloat2D(x: Float, y: Float): MutableFloat2D = FloatMutableVector2D(x, y)

val FloatZeroByZero: Float2D = FloatVector2D(0f, 0f)
val FloatOneByOne: Float2D = FloatVector2D(1f, 1f)

infix fun Float.by(y: Float): Float2D =
	if (this == 0f && y == 0f) FloatZeroByZero
	else if (this == 1f && y == 1f) FloatOneByOne
	else FloatVector2D(this, y)

infix fun Float.mutableBy(y: Float): MutableFloat2D = FloatMutableVector2D(this, y)

operator fun Float2D.div(other: Int2D): Float2D = (x / other.x) by (y / other.y)

private abstract class BaseFloatVector2D : Float2D {

	override fun Float.plus(other: Float): Float = this + other
	override fun Float.minus(other: Float): Float = this - other
	override fun Float.times(other: Float): Float = this * other
	override fun Float.times(other: Double): Double = this * other
	override fun Float.div(other: Float): Float = this / other
	override fun Float.unaryMinus(): Float = +this
	override fun Float.floor(): Float = kotlin.math.floor(this)
	override fun Float.ceil(): Float = kotlin.math.ceil(this)
	override fun Float.toDouble(): Double = this.toDouble()
	override fun compare(a: Float, b: Float): Int = a.compareTo(b)

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

private class FloatVector2D(override val x: Float, override val y: Float) : BaseFloatVector2D(), Float2D {

	override fun newInstance(newX: Float, newY: Float): Float2D = newX by newY
	override fun toMutable(): MutableFloat2D = x mutableBy y
	override fun toDouble2D(): Double2D = x.toDouble() by y.toDouble()
	override fun toFloat2D(): Float2D = this
	override fun toInt2D(): Int2D = x.toInt() by y.toInt()

}

private class FloatMutableVector2D(override var x: Float, override var y: Float) :
	BaseFloatVector2D(), MutableFloat2D {

	override fun newInstance(newX: Float, newY: Float): MutableFloat2D = newX mutableBy newY
	override fun toImmutable(): Float2D = x by y
	override fun toDouble2D(): MutableDouble2D = x.toDouble() mutableBy y.toDouble()
	override fun toFloat2D(): MutableFloat2D = copy()
	override fun toInt2D(): MutableInt2D = x.toInt() mutableBy y.toInt()

}
