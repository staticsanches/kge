package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.spi.KGESPIExtensible
import dev.staticsanches.kge.utils.toUByte
import kotlin.math.pow

/**
 * Extensible service capable of handling [Pixel] manipulations.
 */
interface PixelService : KGESPIExtensible {

	/**
	 * Returns the conversion of the [pixel] to RGB performing, if necessary, alpha blending
	 * with the [matteBackground]. The returned [Pixel.a] should be ignored.
	 */
	fun toRGB(pixel: Pixel, matteBackground: Pixel): Pixel

	fun toGrayscale(pixel: Pixel): UByte
	fun fromGrayscale(grayscale: UByte, alpha: UByte): Pixel

	fun distance2(rgb1: Pixel, rgb2: Pixel): Float

	companion object : PixelService by instance

}

private val instance: PixelService = KGESPIExtensible.getWithHigherPriority()

internal class DefaultPixelService : PixelService {

	override fun toRGB(pixel: Pixel, matteBackground: Pixel): Pixel =
		when (pixel.a) {
			UByte.MAX_VALUE -> pixel
			UByte.MIN_VALUE -> matteBackground
			else -> {
				val alpha = pixel.a.toFloat() / 255.0f
				pixel * alpha + matteBackground * (1 - alpha)
			}
		}

	override fun toGrayscale(pixel: Pixel): UByte =
		(pixel.r.toFloat() * 0.299f + pixel.g.toFloat() * 0.587f + pixel.b.toFloat() * 0.114f).toUByte()

	override fun fromGrayscale(grayscale: UByte, alpha: UByte): Pixel =
		Pixel(grayscale, grayscale, grayscale, alpha)

	override fun distance2(rgb1: Pixel, rgb2: Pixel): Float {
		if (rgb1.r == rgb2.r && rgb1.g == rgb2.g && rgb1.b == rgb2.b) {
			return 0f
		}

		val (l1, a1, b1) = toLab(toXYZ(rgb1))
		val (l2, a2, b2) = toLab(toXYZ(rgb2))

		return (l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2)
	}

	// Code extracted from: http://www.easyrgb.com/en/math.php
	private fun toXYZ(rgb: Pixel): Triple<Float, Float, Float> {
		var r = rgb.r.toFloat() / 255f
		var g = rgb.g.toFloat() / 255f
		var b = rgb.b.toFloat() / 255f

		r = if (r > 0.04045f) ((r + 0.055f) / 1.055f).pow(2.4f) else r / 12.92f
		g = if (g > 0.04045f) ((g + 0.055f) / 1.055f).pow(2.4f) else g / 12.92f
		b = if (b > 0.04045f) ((b + 0.055f) / 1.055f).pow(2.4f) else b / 12.92f

		r *= 100
		g *= 100
		b *= 100

		return Triple(
			r * 0.4124f + g * 0.3576f + b * 0.1805f,
			r * 0.2126f + g * 0.7152f + b * 0.0722f,
			r * 0.0193f + g * 0.1192f + b * 0.9505f
		)
	}

	// Code extracted from: http://www.easyrgb.com/en/math.php
	private fun toLab(xyz: Triple<Float, Float, Float>): Triple<Float, Float, Float> {
		var (x, y, z) = xyz

		// Using D65
		x /= 94.811f
		y /= 100
		z /= 107.304f

		x = if (x > 0.008856) x.pow(1f / 3f) else (7.787f * x) + (16f / 116f)
		y = if (y > 0.008856) y.pow(1f / 3f) else (7.787f * y) + (16f / 116f)
		z = if (z > 0.008856) z.pow(1f / 3f) else (7.787f * z) + (16f / 116f)

		return Triple(
			(116 * y) - 16,
			500 * (x - y),
			200 * (y - z)
		)
	}

	override val servicePriority: Int
		get() = Int.MIN_VALUE

}
