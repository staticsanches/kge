@file:Suppress("unused")

package dev.staticsaches.knes.utils

import kotlin.jvm.JvmInline

@JvmInline
value class UInt16 private constructor(
    val value: UInt,
) : Comparable<UInt16> {
    constructor(value: UInt, disambiguationParameter: Unit = Unit) : this(value and 0xFFFFu)

    operator fun plus(other: UInt8): UInt16 = UInt16((value + other.value) and 0xFFFFu)

    operator fun plus(other: UInt16): UInt16 = UInt16((value + other.value) and 0xFFFFu)

    operator fun plus(other: UInt): UInt16 = UInt16((value + other) and 0xFFFFu)

    operator fun minus(other: UInt8): UInt16 = UInt16((value - other.value) and 0xFFFFu)

    operator fun minus(other: UInt16): UInt16 = UInt16((value - other.value) and 0xFFFFu)

    operator fun minus(other: UInt): UInt16 = UInt16((value - other) and 0xFFFFu)

    infix fun or(other: UInt8): UInt16 = UInt16(value or other.value)

    infix fun or(other: UInt16): UInt16 = UInt16(value or other.value)

    infix fun or(other: UInt): UInt16 = UInt16((value or other) and 0xFFFFu)

    infix fun and(other: UInt8): UInt8 = UInt8(value and other.value)

    infix fun and(other: UInt16): UInt16 = UInt16(value and other.value)

    infix fun and(other: UInt): UInt16 = UInt16(value and other)

    infix fun xor(other: UInt8): UInt16 = UInt16(value xor other.value)

    infix fun xor(other: UInt16): UInt16 = UInt16(value xor other.value)

    infix fun xor(other: UInt): UInt16 = UInt16((value xor other) and 0xFFFFu)

    fun inv(): UInt16 = UInt16(value.inv() and 0xFFFFu)

    operator fun inc(): UInt16 = this + 1u

    operator fun dec(): UInt16 = this - 1u

    infix fun shl(bitCount: Int): UInt16 = UInt16((value shl bitCount) and 0xFFFFu)

    infix fun shr(bitCount: Int): UInt16 = UInt16(value shr bitCount)

    fun toInt(): Int = value.toInt()

    override fun compareTo(other: UInt16): Int = value.compareTo(other.value)

    override fun toString(): String = value.toHexString(hexFormat)

    companion object {
        val x0000u: UInt16 = UInt16(0x0000u)
        val x0001u: UInt16 = UInt16(0x0001u)
        val x0080u: UInt16 = UInt16(0x0080u)
        val x00FFu: UInt16 = UInt16(0x00FFu)
        val x0100u: UInt16 = UInt16(0x0100u)
        val x8000u: UInt16 = UInt16(0x8000u)
        val xFF00u: UInt16 = UInt16(0xFF00u)
        val xFFEEu: UInt16 = UInt16(0xFFEEu)
        val xFFFAu: UInt16 = UInt16(0xFFFAu)
        val xFFFCu: UInt16 = UInt16(0xFFFCu)
        val xFFFDu: UInt16 = UInt16(0xFFFDu)
        val xFFFEu: UInt16 = UInt16(0xFFFEu)
        val xFFFFu: UInt16 = UInt16(0xFFFFu)

        fun Byte.toUInt8(): UInt8 = UInt8(this.toUInt())

        private val hexFormat =
            HexFormat {
                upperCase = true
                number {
                    minLength = 4
                    removeLeadingZeros = true
                }
            }
    }
}
