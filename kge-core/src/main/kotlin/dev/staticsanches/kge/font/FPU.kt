@file:Suppress("unused")

package dev.staticsanches.kge.font

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.by

infix fun FPU.by(y: FPU): FPU2D = FPU2D(this, y)

fun Int.toFPU(): FPU = FPU(this shl 6)

fun Int2D.toFPU2D(): FPU2D = x.toFPU() by y.toFPU()

/**
 * Fractional Pixel Unit (26.6 pixel units).
 */
@JvmInline
value class FPU internal constructor(
    val value: Int,
) {
    constructor(value: Long) : this(value.toInt())

    operator fun plus(other: FPU): FPU = FPU(value + other.value)

    operator fun minus(other: FPU): FPU = FPU(value - other.value)

    fun toInt(): Int = value shr 6

    fun toFloat(): Float = value / 64f

    fun toNumber(): Number = if (value shl 26 != 0) toFloat() else toInt()

    override fun toString(): String = toNumber().toString()
}

data class FPU2D(
    val x: FPU,
    val y: FPU,
) {
    fun toInt2D(): Int2D = x.toInt() by y.toInt()

    fun toFloat2D(): Float2D = x.toFloat() by y.toFloat()

    operator fun plus(other: FPU2D): FPU2D = (x + other.x) by (y + other.y)

    override fun toString(): String = "($x, $y)"
}
