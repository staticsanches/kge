package dev.staticsanches.kge.math.vector

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A 2D point/vector over single-precision components — a plain value type:
 * equality, hash and destructuring come from the `data` machinery, [toString]
 * renders the `(x, y)` pair through `Float.toString`. Arithmetic is
 * componentwise and overloaded by operator; transcendental results follow the
 * platform's `kotlin.math`.
 */
data class Float2D(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: Float2D): Float2D = Float2D(x + other.x, y + other.y)

    operator fun minus(other: Float2D): Float2D = Float2D(x - other.x, y - other.y)

    operator fun times(other: Float2D): Float2D = Float2D(x * other.x, y * other.y)

    operator fun div(other: Float2D): Float2D = Float2D(x / other.x, y / other.y)

    operator fun times(scalar: Float): Float2D = Float2D(x * scalar, y * scalar)

    operator fun div(scalar: Float): Float2D = Float2D(x / scalar, y / scalar)

    /**
     * Divides by an integer vector componentwise — the inverted-screen form; a
     * zero [Int2D] component yields ±Infinity/`NaN` per IEEE on every target,
     * unlike [Int2D.div] which throws.
     */
    operator fun div(other: Int2D): Float2D = Float2D(x / other.x, y / other.y)

    operator fun unaryMinus(): Float2D = Float2D(-x, -y)

    /** The signed component product, `x * y`. */
    fun area(): Float = x * y

    /** The length. */
    fun mag(): Float = sqrt(mag2())

    /** The squared length. */
    fun mag2(): Float = x * x + y * y

    /** The unit vector, mirroring the reference's `this * (1 / mag())`. */
    fun norm(): Float2D = this * (1f / mag())

    /** The counter-clockwise quarter turn of this vector, `(-y, x)`. */
    fun perp(): Float2D = Float2D(-y, x)

    /** Each component rounded down. */
    fun floor(): Float2D = Float2D(floor(x), floor(y))

    /** Each component rounded up. */
    fun ceil(): Float2D = Float2D(ceil(x), ceil(y))

    /** The componentwise smaller vector. */
    fun min(other: Float2D): Float2D = Float2D(minOf(x, other.x), minOf(y, other.y))

    /** The componentwise larger vector. */
    fun max(other: Float2D): Float2D = Float2D(maxOf(x, other.x), maxOf(y, other.y))

    /** The component product sum, `x * ox + y * oy`. */
    fun dot(other: Float2D): Float = x * other.x + y * other.y

    /** The signed area of the parallelogram, `x * oy - y * ox`. */
    fun cross(other: Float2D): Float = x * other.y - y * other.x

    /** Bounds each component to the low/high rectangle: `max(lo).min(hi)`. */
    fun clamp(
        low: Float2D,
        high: Float2D,
    ): Float2D = max(low).min(high)

    /** The linear interpolation between this vector and [other] at [t]. */
    fun lerp(
        other: Float2D,
        t: Float,
    ): Float2D = this * (1f - t) + other * t

    /** Radius and angle from the origin, `(mag, atan2(y, x))`. */
    fun polar(): Float2D = Float2D(mag(), atan2(y, x))

    /** Cartesian from radius and angle, `(cos(y) * x, sin(y) * x)`. */
    fun cart(): Float2D = Float2D(cos(y) * x, sin(y) * x)

    /** Mirrors this vector around [normal], `this - normal * (2 * dot(normal))`. */
    fun reflect(normal: Float2D): Float2D = this - normal * (2f * dot(normal))

    /** Narrows both components, truncating toward zero. */
    fun toInt(): Int2D = Int2D(x.toInt(), y.toInt())

    override fun toString(): String = "($x, $y)"
}
