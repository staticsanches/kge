@file:Suppress("UNUSED_PARAMETER", "unused", "MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.utils.or
import dev.staticsanches.kge.utils.shl
import dev.staticsanches.kge.utils.toUByte
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min


/**
 * Represents a 32-Bit RGBA color.
 *
 * Since KGE works with low-level libraries, [nativeRGBA] stores the RGBA color using
 * [ByteOrder.nativeOrder] to minimize the number of conversions needed when working
 * with [PixelBuffer.RGBA].
 */
@JvmInline
value class Pixel(val nativeRGBA: Int) {

	constructor(
		rgba: UInt, parameterToAvoidPlatformDeclarationClash: Unit = Unit
	) : this(if (isLittleEndian) Integer.reverseBytes(rgba.toInt()) else rgba.toInt())

	constructor(r: UByte, g: UByte, b: UByte, a: UByte = 0xFFu) : this(
		if (isLittleEndian) (r or (g shl 8) or (b shl 16) or (a shl 24)).toInt()
		else ((r shl 24) or (g shl 16) or (b shl 8) or a).toInt()
	)

	val r: UByte
		get() = (if (isLittleEndian) nativeRGBA else nativeRGBA shr 24).toUByte()
	val g: UByte
		get() = (if (isLittleEndian) nativeRGBA shr 8 else nativeRGBA shr 16).toUByte()
	val b: UByte
		get() = (if (isLittleEndian) nativeRGBA shr 16 else nativeRGBA shr 8).toUByte()
	val a: UByte
		get() = (if (isLittleEndian) nativeRGBA shr 24 else nativeRGBA).toUByte()
	val rgba: UInt
		get() = if (isLittleEndian) Integer.reverseBytes(nativeRGBA).toUInt() else nativeRGBA.toUInt()

	fun inv(): Pixel = !this

	fun lerp(p1: Pixel, t: Float): Pixel = this * (1.0f - t) + p1 * t

	operator fun plus(other: Pixel): Pixel = Pixel(
		validComponent(r.toInt() + other.r.toInt()),
		validComponent(g.toInt() + other.g.toInt()),
		validComponent(b.toInt() + other.b.toInt()),
		a
	)

	operator fun minus(other: Pixel): Pixel = Pixel(
		validComponent(r.toInt() - other.r.toInt()),
		validComponent(g.toInt() - other.g.toInt()),
		validComponent(b.toInt() - other.b.toInt()),
		a
	)

	operator fun times(f: Float): Pixel =
		Pixel(validComponent(r * f), validComponent(g * f), validComponent(b * f), a)

	operator fun div(f: Float): Pixel =
		Pixel(validComponent(r / f), validComponent(g / f), validComponent(b / f), a)

	operator fun not(): Pixel = Pixel(
		validComponent(255 - r.toInt()),
		validComponent(255 - g.toInt()),
		validComponent(255 - b.toInt()),
		a
	)

	fun toString(format: Format): String = when (format) {
		Format.RGBA -> "rgba($r, $g, $b, $a)"
		Format.HEX -> "#" + rgba.toString(16).uppercase().padStart(8, '0')
	}

	override fun toString(): String = toString(Format.HEX)

	enum class Format { RGBA, HEX }

	private companion object {

		private val isLittleEndian = ByteOrder.LITTLE_ENDIAN == ByteOrder.nativeOrder()

		private fun validComponent(value: Float): UByte = min(255f, max(0f, value)).toUByte()
		private fun validComponent(value: Int): UByte = min(255, max(0, value)).toUByte()

		private operator fun UByte.times(value: Float): Float = this.toFloat() * value
		private operator fun UByte.div(value: Float): Float = this.toFloat() / value

	}

}
