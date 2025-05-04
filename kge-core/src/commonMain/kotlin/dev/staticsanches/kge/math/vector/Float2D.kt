@file:Suppress("unused")

package dev.staticsanches.kge.math.vector

import kotlin.jvm.JvmInline

@JvmInline
value class Float2D private constructor(
    private val xy: Long,
) {
    constructor(
        x: Float,
        y: Float,
    ) : this((x.toBits().toLong() shl 32) or (y.toBits().toLong() and 0xffffffffL))

    val x: Float
        get() = Float.fromBits((xy shr 32).toInt())
    val y: Float
        get() = Float.fromBits(xy.toInt())

    operator fun plus(other: Float2D): Float2D = Float2D(x + other.x, y + other.y)

    operator fun minus(other: Float2D): Float2D = Float2D(x - other.x, y - other.y)

    operator fun div(other: Float2D): Float2D = Float2D(x / other.x, y / other.y)

    operator fun div(other: Int2D): Float2D = Float2D(x / other.x, y / other.y)

    operator fun component1(): Float = x

    operator fun component2(): Float = y

    override fun toString(): String = "($x, $y)"

    companion object {
        val zeroByZero: Float2D = Float2D(0L)
        val oneByOne: Float2D = Float2D(1f, 1f)

        infix fun Float.by(y: Float): Float2D = Float2D(this, y)
    }
}
