@file:Suppress("unused")

package dev.staticsanches.kge.utils

infix fun UByte.or(other: UShort): UShort = (this.toInt() or other.toInt()).toUShort()
infix fun UByte.or(other: UInt): UInt = this.toUInt() or other
infix fun UByte.and(other: UShort): UShort = (this.toInt() and other.toInt()).toUShort()
infix fun UByte.and(other: UInt): UInt = this.toUInt() and other
infix fun UByte.shl(bitCount: Int): UInt = this.toUInt() shl bitCount
infix fun UByte.shr(bitCount: Int): UInt = this.toUInt() shr bitCount

@OptIn(ExperimentalStdlibApi::class)
val UByte.hex: String
	get() = this.toHexString(HexFormat.UpperCase)

infix fun UShort.or(other: UByte): UShort = (this.toInt() or other.toInt()).toUShort()
infix fun UShort.or(other: UInt): UInt = this.toUInt() or other
infix fun UShort.and(other: UByte): UShort = (this.toInt() and other.toInt()).toUShort()
infix fun UShort.and(other: UInt): UInt = this.toUInt() and other
infix fun UShort.shl(bitCount: Int): UInt = this.toUInt() shl bitCount
infix fun UShort.shr(bitCount: Int): UInt = this.toUInt() shr bitCount

infix fun UInt.or(other: UByte): UInt = other or this
infix fun UInt.or(other: UShort): UInt = other or this
infix fun UInt.and(other: UByte): UInt = other and this
infix fun UInt.and(other: UShort): UInt = other and this

fun Float.toUByte(): UByte = this.toUInt().toUByte()
fun Float.toUShort(): UShort = this.toUInt().toUShort()
