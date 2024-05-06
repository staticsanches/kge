@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.endian.EndianAwareUtils
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.alphaFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.blueFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.greenFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.redFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.toNativeRGBA
import dev.staticsanches.kge.endian.KGEEndianDependent


/**
 * Represents an [Int] value x that should lie in 0 <= x <= 255.
 */
typealias IntColorComponent = Int

/**
 * Represents a [Float] value x that should lie in 0 <= x <= 1.
 */
typealias FloatColorComponent = Float

/**
 * Represents a 32-Bit RGBA color.
 *
 * @see EndianAwareUtils.toNativeRGBA
 */
@JvmInline
value class Pixel @KGEEndianDependent constructor(val nativeRGBA: Int) {

	val r: IntColorComponent
		get() = redFromNativeRGBA(nativeRGBA)
	val g: IntColorComponent
		get() = greenFromNativeRGBA(nativeRGBA)
	val b: IntColorComponent
		get() = blueFromNativeRGBA(nativeRGBA)
	val a: IntColorComponent
		get() = alphaFromNativeRGBA(nativeRGBA)
	val rgba: Int
		get() = EndianAwareUtils.fromNativeRGBA(nativeRGBA)

	operator fun component0(): IntColorComponent = r
	operator fun component1(): IntColorComponent = g
	operator fun component2(): IntColorComponent = b
	operator fun component3(): IntColorComponent = a

	fun inv(): Pixel = rgba(255 - r, 255 - g, 255 - b, a)
	fun lerp(p1: Pixel, t: Float): Pixel = this * (1 - t) + p1 * t

	operator fun plus(other: Pixel): Pixel = rgba(r + other.r, g + other.g, b + other.b, a)

	operator fun minus(other: Pixel): Pixel = rgba(r - other.r, g - other.g, b - other.b, a)

	operator fun times(factor: Float): Pixel =
		rgba((r * factor).toInt(), (g * factor).toInt(), (b * factor).toInt(), a)

	operator fun div(factor: Float): Pixel =
		rgba((r / factor).toInt(), (g / factor).toInt(), (b / factor).toInt(), a)

	fun toString(format: Format): String =
		when (format) {
			Format.RGBA -> "rgba($r, $g, $b, $a)"
			Format.HEX -> "#" + rgba.toString(16).uppercase().padStart(8, '0')
		}

	override fun toString(): String = toString(Format.HEX)

	enum class Format { RGBA, HEX }

	@OptIn(KGEEndianDependent::class)
	companion object {

		fun uRGBA(rgba: UInt): Pixel = Pixel(toNativeRGBA(rgba.toInt()))

		fun rgba(rgba: Int): Pixel = Pixel(toNativeRGBA(rgba))

		fun rgba(r: IntColorComponent, g: IntColorComponent, b: IntColorComponent, a: IntColorComponent = 0xFF): Pixel =
			Pixel(toNativeRGBA(r, g, b, a))

		fun rgba(
			r: FloatColorComponent, g: FloatColorComponent, b: FloatColorComponent, a: FloatColorComponent = 1f
		): Pixel = Pixel(toNativeRGBA((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), (a * 255).toInt()))

		fun fromNativeRGBA(nativeRGBA: Int): Pixel = Pixel(nativeRGBA)

	}

}
