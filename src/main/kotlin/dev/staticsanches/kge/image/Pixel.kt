@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.endian.EndianAwareUtils
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.alphaFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.blueFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.greenFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.invNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.redFromNativeRGBA
import dev.staticsanches.kge.endian.EndianAwareUtils.Companion.toNativeRGBA
import dev.staticsanches.kge.endian.KGEEndianDependent
import kotlin.math.max
import kotlin.math.min


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
	val rgba: UInt
		get() = EndianAwareUtils.fromNativeRGBA(nativeRGBA)

	operator fun component1(): IntColorComponent = r
	operator fun component2(): IntColorComponent = g
	operator fun component3(): IntColorComponent = b
	operator fun component4(): IntColorComponent = a

	fun inv(): Pixel = fromNativeRGBA(invNativeRGBA(nativeRGBA))

	/**
	 * Calculate the linear interpolation of this (start) and the informed [end].
	 */
	fun lerp(end: Pixel, t: Float): Pixel = this * (1 - t) + end * t

	operator fun plus(other: Pixel): Pixel =
		rgba(r + other.r, g + other.g, b + other.b, a)

	operator fun minus(other: Pixel): Pixel =
		rgba(r - other.r, g - other.g, b - other.b, a)

	operator fun times(factor: Float): Pixel =
		rgba((r * factor).toInt(), (g * factor).toInt(), (b * factor).toInt(), a)

	operator fun div(factor: Float): Pixel =
		rgba((r / factor).toInt(), (g / factor).toInt(), (b / factor).toInt(), a)

	override fun toString(): String = Format.HEX(this)

	sealed interface Mode {

		data object Normal : Mode

		data object Mask : Mode

		@JvmInline
		value class Alpha private constructor(val blendFactor: Float) : Mode {

			constructor(
				blendFactor: Float,
				@Suppress("UNUSED_PARAMETER") parameterToAvoidPlatformDeclarationClash: Boolean = true
			) : this(max(0f, min(1f, blendFactor)))

		}

		interface Custom : Mode {

			operator fun invoke(x: Int, y: Int, newPixel: Pixel, oldPixel: Pixel): Pixel

		}

	}

	enum class Format {

		RGBA, HEX;

		operator fun invoke(pixel: Pixel): String =
			when (this) {
				RGBA -> "rgba(${pixel.r}, ${pixel.g}, ${pixel.b}, ${pixel.a})"
				HEX -> "#" + pixel.rgba.toString(16).uppercase().padStart(8, '0')
			}

	}

	@OptIn(KGEEndianDependent::class)
	companion object {

		fun rgba(rgba: UInt): Pixel = Pixel(toNativeRGBA(rgba.toInt()))

		fun rgba(r: IntColorComponent, g: IntColorComponent, b: IntColorComponent, a: IntColorComponent = 0xFF): Pixel =
			Pixel(toNativeRGBA(r, g, b, a))

		fun rgba(
			r: FloatColorComponent, g: FloatColorComponent, b: FloatColorComponent, a: FloatColorComponent = 1f
		): Pixel = Pixel(toNativeRGBA((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), (a * 255).toInt()))

		fun fromNativeRGBA(nativeRGBA: Int): Pixel = Pixel(nativeRGBA)

	}

}
