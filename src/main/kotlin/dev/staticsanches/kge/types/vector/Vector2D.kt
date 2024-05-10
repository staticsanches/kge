@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package dev.staticsanches.kge.types.vector

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * Represents a vector ([x], [y]) in a 2D plane.
 */
interface Vector2D<T> {

	val x: T
	val y: T

	fun mag(): Double = sqrt(mag2().toDouble())

	fun mag2(): T = this dot this

	infix fun dot(other: Vector2D<T>): T = x * other.x + y * other.y

	infix fun cross(other: Vector2D<T>): T = x * other.y - y * other.x

	fun normalized(): Double2D = this.toDouble2D() / mag()

	fun perpendicular(): Vector2D<T> = copy(-y, x)

	fun floor(): Vector2D<T> = copy(x.floor(), y.floor())

	fun ceil(): Vector2D<T> = copy(x.ceil(), y.ceil())

	infix fun max(other: Vector2D<T>): Vector2D<T> =
		copy(maxOf(x, other.x, this::compare), maxOf(y, other.y, this::compare))

	infix fun min(other: Vector2D<T>): Vector2D<T> =
		copy(minOf(x, other.x, this::compare), minOf(y, other.y, this::compare))

	fun cartesian(): Double2D {
		val yAsDouble = y.toDouble()
		return (x * cos(yAsDouble)) by (x * sin(yAsDouble))
	}

	fun polar(): Double2D = mag() by (atan2(y.toDouble(), x.toDouble()))

	fun clamp(v1: Vector2D<T>, v2: Vector2D<T>): Vector2D<T> = (this max v1) min v2

	fun lerp(v1: Vector2D<*>, t: Double): Double2D = this.toDouble2D() * (1.0 - t) + v1.toDouble2D() * t

	operator fun plus(other: Vector2D<T>): Vector2D<T> = copy(x + other.x, y + other.y)

	operator fun minus(other: Vector2D<T>): Vector2D<T> = copy(x - other.x, y - other.y)

	operator fun times(other: Vector2D<T>): Vector2D<T> = copy(x * other.x, y * other.y)

	operator fun times(value: T): Vector2D<T> = copy(x * value, y * value)

	operator fun div(other: Vector2D<T>): Vector2D<T> = copy(x / other.x, y / other.y)

	operator fun div(value: T): Vector2D<T> = copy(x / value, y / value)

	operator fun unaryPlus() = this

	operator fun unaryMinus() = copy(-x, -y)

	operator fun component1(): T = x

	operator fun component2(): T = y

	fun toImmutable(): Vector2D<T> = this
	fun toMutable(): MutableVector2D<T>

	fun toDouble2D(): Double2D
	fun toFloat2D(): Float2D
	fun toInt2D(): Int2D

	fun copy(x: T = this.x, y: T = this.y): Vector2D<T> =
		if (this.x == x && this.y == y) this else newInstance(x, y)

	fun newInstance(newX: T, newY: T): Vector2D<T>
	fun compare(a: T, b: T): Int

	operator fun T.plus(other: T): T
	operator fun T.minus(other: T): T
	operator fun T.times(other: T): T
	operator fun T.times(other: Double): Double
	operator fun T.div(other: T): T
	operator fun T.unaryMinus(): T
	fun T.toDouble(): Double
	fun T.floor(): T
	fun T.ceil(): T

}

interface MutableVector2D<T> : Vector2D<T> {

	override var x: T
	override var y: T

	override fun normalized(): MutableDouble2D = this.toDouble2D() / mag()

	override fun perpendicular(): MutableVector2D<T> = copy(-y, x)

	override fun floor(): MutableVector2D<T> = copy(x.floor(), y.floor())

	override fun ceil(): MutableVector2D<T> = copy(x.ceil(), y.ceil())

	override infix fun max(other: Vector2D<T>): MutableVector2D<T> =
		copy(maxOf(x, other.x, this::compare), maxOf(y, other.y, this::compare))

	override infix fun min(other: Vector2D<T>): MutableVector2D<T> =
		copy(minOf(x, other.x, this::compare), minOf(y, other.y, this::compare))

	override fun cartesian(): MutableDouble2D {
		val yAsDouble = y.toDouble()
		return (x * cos(yAsDouble)) mutableBy (x * sin(yAsDouble))
	}

	override fun polar(): MutableDouble2D = mag() mutableBy (atan2(y.toDouble(), x.toDouble()))

	override fun clamp(v1: Vector2D<T>, v2: Vector2D<T>): MutableVector2D<T> = (this max v1) min v2

	override fun lerp(v1: Vector2D<*>, t: Double): MutableDouble2D = this.toDouble2D() * (1.0 - t) + v1.toDouble2D() * t

	override operator fun plus(other: Vector2D<T>): MutableVector2D<T> = copy(x + other.x, y + other.y)

	override operator fun minus(other: Vector2D<T>): MutableVector2D<T> = copy(x - other.x, y - other.y)

	override operator fun times(other: Vector2D<T>): MutableVector2D<T> = copy(x * other.x, y * other.y)

	override operator fun times(value: T): MutableVector2D<T> = copy(x * value, y * value)

	override operator fun div(other: Vector2D<T>): MutableVector2D<T> = copy(x / other.x, y / other.y)

	override operator fun div(value: T): MutableVector2D<T> = copy(x / value, y / value)

	override operator fun unaryPlus() = copy()

	override operator fun unaryMinus() = copy(-x, -y)

	operator fun plusAssign(other: Vector2D<T>) {
		x += other.x
		y += other.y
	}

	operator fun minusAssign(other: Vector2D<T>) {
		x -= other.x
		y -= other.y
	}

	operator fun timesAssign(other: Vector2D<T>) {
		x *= other.x
		y *= other.y
	}

	operator fun timesAssign(value: T) {
		x *= value
		y *= value
	}

	operator fun divAssign(other: Vector2D<T>) {
		x /= other.x
		y /= other.y
	}

	operator fun divAssign(value: T) {
		x /= value
		y /= value
	}

	infix fun swappedWith(other: MutableVector2D<T>) {
		x = other.x.also { other.x = x }
		y = other.y.also { other.y = y }
	}

	override fun toImmutable(): Vector2D<T>

	override fun toMutable(): MutableVector2D<T> = copy()

	override fun toDouble2D(): MutableDouble2D

	override fun toFloat2D(): MutableFloat2D

	override fun toInt2D(): MutableInt2D

	override fun copy(x: T, y: T): MutableVector2D<T> = newInstance(x, y)

	override fun newInstance(newX: T, newY: T): MutableVector2D<T>

}
