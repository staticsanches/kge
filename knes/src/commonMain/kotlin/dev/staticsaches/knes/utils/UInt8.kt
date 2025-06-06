@file:Suppress("unused")

package dev.staticsaches.knes.utils

import kotlin.jvm.JvmInline

@JvmInline
value class UInt8 private constructor(
    val value: UInt,
) : Comparable<UInt8> {
    constructor(value: UInt, disambiguationParameter: Unit = Unit) : this(value and 0xFFu)

    operator fun plus(other: UInt8): UInt8 = UInt8((value + other.value) and 0xFFu)

    operator fun plus(other: UInt16): UInt16 = UInt16(value + other.value)

    operator fun plus(other: UInt): UInt8 = UInt8((value + other) and 0xFFu)

    operator fun minus(other: UInt8): UInt8 = UInt8((value - other.value) and 0xFFu)

    operator fun minus(other: UInt16): UInt16 = UInt16(value - other.value)

    operator fun minus(other: UInt): UInt8 = UInt8((value - other) and 0xFFu)

    infix fun or(other: UInt8): UInt8 = UInt8(value or other.value)

    infix fun or(other: UInt16): UInt16 = UInt16(value or other.value)

    infix fun or(other: UInt): UInt8 = UInt8((value or other) and 0xFFu)

    infix fun and(other: UInt8): UInt8 = UInt8(value and other.value)

    infix fun and(other: UInt16): UInt8 = UInt8(value and other.value)

    infix fun and(other: UInt): UInt8 = UInt8(value and other)

    infix fun xor(other: UInt8): UInt8 = UInt8(value xor other.value)

    infix fun xor(other: UInt16): UInt16 = UInt16(value xor other.value)

    infix fun xor(other: UInt): UInt8 = UInt8((value xor other) and 0xFFu)

    fun inv(): UInt8 = UInt8(value.inv() and 0xFFu)

    operator fun inc(): UInt8 = this + 1u

    operator fun dec(): UInt8 = this - 1u

    infix fun shl(bitCount: Int): UInt16 = UInt16(value shl bitCount)

    infix fun shr(bitCount: Int): UInt8 = UInt8(value shr bitCount)

    fun toInt(): Int = value.toInt()

    fun toUInt16(): UInt16 = UInt16(value)

    override fun compareTo(other: UInt8): Int = value.compareTo(other.value)

    override fun toString(): String = value.toHexString(hexFormat)

    companion object {
        val x00u: UInt8 = UInt8(0x00u)
        val x01u: UInt8 = UInt8(0x01u)
        val x80u: UInt8 = UInt8(0x80u)
        val xFDu: UInt8 = UInt8(0xFDu)
        val xFFu: UInt8 = UInt8(0xFFu)

        private val hexFormat =
            HexFormat {
                upperCase = true
                number {
                    minLength = 2
                    removeLeadingZeros = true
                }
            }
    }
}
