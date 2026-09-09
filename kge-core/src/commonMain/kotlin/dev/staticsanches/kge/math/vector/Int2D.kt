package dev.staticsanches.kge.math.vector

import kotlin.math.sqrt

/**
 * A 2D point/vector over integer components — a plain value type: equality,
 * hash and destructuring come from the `data` machinery, [toString] renders
 * the `(x, y)` pair. Arithmetic is componentwise and overloaded by operator;
 * division truncates like `Int / Int` for nonzero divisors, and dividing by a zero
 * component throws `ArithmeticException` on every target — Kotlin/JS `Int / Int` does
 * not throw; Int2D pins the JVM/wasm behavior uniformly.
 */
data class Int2D(
    val x: Int,
    val y: Int,
) {
    operator fun plus(other: Int2D): Int2D = Int2D(x + other.x, y + other.y)

    operator fun minus(other: Int2D): Int2D = Int2D(x - other.x, y - other.y)

    operator fun times(other: Int2D): Int2D = Int2D(x * other.x, y * other.y)

    operator fun div(other: Int2D): Int2D = Int2D(x.checkedDiv(other.x), y.checkedDiv(other.y))

    operator fun times(scalar: Int): Int2D = Int2D(x * scalar, y * scalar)

    operator fun div(scalar: Int): Int2D = Int2D(x.checkedDiv(scalar), y.checkedDiv(scalar))

    operator fun unaryMinus(): Int2D = Int2D(-x, -y)

    /** The counter-clockwise quarter turn of this vector, `(-y, x)`. */
    fun perp(): Int2D = Int2D(-y, x)

    /** The component product sum, `x * ox + y * oy`. */
    fun dot(other: Int2D): Int = x * other.x + y * other.y

    /** The signed area of the parallelogram, `x * oy - y * ox`. */
    fun cross(other: Int2D): Int = x * other.y - y * other.x

    /** The squared length, `x * x + y * y`, wrapping like `Int` arithmetic. */
    fun mag2(): Int = x * x + y * y

    /**
     * The double-precision length, `sqrt(mag2())`; on large coordinates
     * `mag2()` may wrap negative and `sqrt` yields `NaN`.
     */
    fun mag(): Double = sqrt(mag2().toDouble())

    /** The signed component product, `x * y`. */
    fun area(): Int = x * y

    /** The componentwise smaller vector. */
    fun min(other: Int2D): Int2D = Int2D(minOf(x, other.x), minOf(y, other.y))

    /** The componentwise larger vector. */
    fun max(other: Int2D): Int2D = Int2D(maxOf(x, other.x), maxOf(y, other.y))

    /** Bounds each component to the low/high rectangle: `max(lo).min(hi)`. */
    fun clamp(
        low: Int2D,
        high: Int2D,
    ): Int2D = max(low).min(high)

    /** Widens both components to float. */
    fun toFloat(): Float2D = Float2D(x.toFloat(), y.toFloat())

    override fun toString(): String = "($x, $y)"
}

/**
 * Kotlin/JS `Int / Int` on a zero divisor does not throw and yields a
 * defined-but-non-JVM result — the guard pins the uniform
 * `ArithmeticException` on all targets.
 */
private fun Int.checkedDiv(divisor: Int): Int {
    if (divisor == 0) throw ArithmeticException("/ by zero")
    return this / divisor
}
