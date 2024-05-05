@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER", "unused")

package dev.staticsanches.kge.types.vector


typealias Double2D = Vector2D<Double>
typealias MutableDouble2D = MutableVector2D<Double>

fun Double2D(x: Double, y: Double): Double2D = x by y
fun MutableDouble2D(x: Double, y: Double): MutableDouble2D = DoubleMutableVector2D(x, y)

val DoubleZeroByZero: Double2D = DoubleVector2D(0.0, 0.0)
val DoubleOneByOne: Double2D = DoubleVector2D(1.0, 1.0)

infix fun Double.by(y: Double): Double2D =
	if (this == 0.0 && y == 0.0) DoubleZeroByZero
	else if (this == 1.0 && y == 1.0) DoubleOneByOne
	else DoubleVector2D(this, y)

infix fun Double.mutableBy(y: Double): MutableDouble2D = DoubleMutableVector2D(this, y)

private abstract class BaseDoubleVector2D : Double2D {

	override fun Double.plus(other: Double): Double = this + other
	override fun Double.minus(other: Double): Double = this - other
	override fun Double.times(other: Double): Double = this * other
	override fun Double.div(other: Double): Double = this / other
	override fun Double.unaryMinus(): Double = +this
	override fun Double.floor(): Double = kotlin.math.floor(this)
	override fun Double.ceil(): Double = kotlin.math.ceil(this)
	override fun Double.toDouble(): Double = this
	override fun compare(a: Double, b: Double): Int = a.compareTo(b)

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

private class DoubleVector2D(override val x: Double, override val y: Double) : BaseDoubleVector2D(), Double2D {

	override fun newInstance(newX: Double, newY: Double): Double2D = newX by newY
	override fun toMutable(): MutableDouble2D = x mutableBy y
	override fun toDouble2D(): Double2D = this
	override fun toFloat2D(): Float2D = x.toFloat() by y.toFloat()
	override fun toInt2D(): Int2D = x.toInt() by y.toInt()

}

private class DoubleMutableVector2D(override var x: Double, override var y: Double) :
	BaseDoubleVector2D(), MutableDouble2D {

	override fun newInstance(newX: Double, newY: Double): MutableDouble2D = newX mutableBy newY
	override fun toImmutable(): Double2D = x by y
	override fun toDouble2D(): MutableDouble2D = copy()
	override fun toFloat2D(): MutableFloat2D = x.toFloat() mutableBy y.toFloat()
	override fun toInt2D(): MutableInt2D = x.toInt() mutableBy y.toInt()

}
