@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.Int2D
import dev.staticsanches.kge.utils.toUByte
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


/**
 * Representation of an image in KGE.
 *
 * Uses a [PixelBuffer.RGBA] to store and manipulate the image data.
 */
abstract class Sprite(val pixmap: PixelBuffer.RGBA, var sampleMode: SampleMode = SampleMode.NORMAL) :
	Sequence<Pixel> by pixmap, KGEResource by pixmap {

	val width: Int
		get() = pixmap.width
	val height: Int
		get() = pixmap.height
	val size: Int2D
		get() = pixmap.size

	fun getPixel(x: Int, y: Int): Pixel = this[x, y]
	fun getPixel(position: Int2D): Pixel = this[position]

	operator fun get(x: Int, y: Int): Pixel = when (sampleMode) {
		SampleMode.NORMAL -> if (x in 0..<width && y in 0..<height) uncheckedGet(x, y) else Colors.BLANK
		SampleMode.PERIODIC -> uncheckedGet(abs(x % width), abs(y % height))
		SampleMode.CLAMP -> uncheckedGet(max(0, min(x, width - 1)), max(0, min(y, height - 1)))
	}

	operator fun get(position: Int2D): Pixel = this[position.x, position.y]

	fun uncheckedGet(x: Int, y: Int): Pixel = pixmap.uncheckedGet(x, y)

	fun setPixel(x: Int, y: Int, pixel: Pixel): Boolean = set(x, y, pixel)

	fun setPixel(position: Int2D, pixel: Pixel): Boolean = set(position, pixel)

	operator fun set(x: Int, y: Int, pixel: Pixel): Boolean =
		x in 0..<width && y in 0..<height && uncheckedSet(x, y, pixel)

	operator fun set(position: Int2D, pixel: Pixel): Boolean = set(position.x, position.y, pixel)

	fun uncheckedSet(x: Int, y: Int, pixel: Pixel): Boolean {
		pixmap.uncheckedSet(x, y, pixel)
		return true
	}

	fun clear(pixel: Pixel) {
		pixmap.clear(pixel)
	}

	fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
		pixmap.clear(pixelByXY)
	}

	fun sample(x: Float, y: Float): Pixel =
		this[min((x * width).toInt(), width - 1), min((y * height).toInt(), height - 1)]

	fun sample(position: Float2D): Pixel = sample(position.x, position.y)

	fun sampleBL(u: Float, v: Float): Pixel {
		val computedU = u * width - 0.5f
		val computedV = v * width - 0.5f

		val x = floor(computedU).toInt()
		val y = floor(computedV).toInt()
		val uRatio = computedU - x
		val vRatio = computedV - y
		val uOpposite = 1 - uRatio
		val vOpposite = 1 - vRatio

		val x0 = max(x, 0)
		val y0 = max(y, 0)
		val x1 = min(x + 1, width - 1)
		val y1 = min(y + 1, height - 1)

		val p1 = this[x0, y0]
		val p2 = this[x1, y0]
		val p3 = this[x0, y1]
		val p4 = this[x1, y1]

		fun calculateComponent(componentGetter: (Pixel) -> UByte): UByte {
			val v1 = componentGetter(p1).toInt()
			val v2 = componentGetter(p2).toInt()
			val v3 = componentGetter(p3).toInt()
			val v4 = componentGetter(p4).toInt()

			return ((v1 * uOpposite + v2 * uRatio) * vOpposite + (v3 * uOpposite + v4 * uRatio) * vRatio).toUByte()
		}

		return Pixel(
			calculateComponent(Pixel::r), calculateComponent(Pixel::g), calculateComponent(Pixel::b)
		)
	}

	override fun toString(): String = "Sprite $pixmap"

	/**
	 * Available [Sprite] types.
	 */
	sealed interface Type {

		/**
		 * Represents images with 4-channels.
		 */
		data object RGBA : Type {

			override fun expectedBufferCapacity(width: Int, height: Int): Int = width * height * 4

		}

		/**
		 * Represents images with 3-channels.
		 *
		 * [defaultAlpha] is used when converting [RGB] -> [RGBA].
		 *
		 * [matteBackground] must be a matte color since its [Pixel.a] is
		 * discarded by [PixelService.toRGB] when converting [RGBA] -> [RGB].
		 */
		data class RGB(val defaultAlpha: UByte = 0xFFu, val matteBackground: Pixel = Colors.WHITE) : Type {

			override fun expectedBufferCapacity(width: Int, height: Int): Int = width * height * 3

		}

		/**
		 * Represents images with 1-channel.
		 *
		 * [PixelService.toGrayscale] is used when converting [RGBA] -> [Grayscale].
		 *
		 * [PixelService.fromGrayscale] and [defaultAlpha] are used when converting [Grayscale] -> [RGBA].
		 */
		data class Grayscale(val defaultAlpha: UByte = 0xFFu) : Type {

			override fun expectedBufferCapacity(width: Int, height: Int): Int = width * height

		}

		/**
		 * Represents images with 1 bit per pixel (aka Black and White).
		 *
		 * [foreground] and [background] are used when converting [Bitmap] -> [RGBA].
		 *
		 * [PixelService.toRGB] and [PixelService.distance2] and [matteBackground] are used
		 * when converting [RGBA] -> [Bitmap].
		 */
		data class Bitmap(
			val foreground: Pixel = Colors.BLACK,
			val background: Pixel = Colors.WHITE,
			val matteBackground: Pixel = Colors.WHITE,
			val negativePitch: Boolean = false,
			val disableEvenPitch: Boolean = false
		) : Type {

			/**
			 * The expected number of bytes in a row.
			 */
			fun expectedAbsolutePitch(width: Int): Int {
				var pitch = width / 8
				if (width % 8 == 0) {
					pitch++
				}
				if (!disableEvenPitch && pitch % 2 == 1) {
					pitch++
				}
				return pitch
			}

			override fun expectedBufferCapacity(width: Int, height: Int): Int =
				expectedAbsolutePitch(width) * height

		}

		/**
		 * The expected [ByteBuffer.capacity] for this [Type].
		 */
		fun expectedBufferCapacity(width: Int, height: Int): Int

	}

	enum class SampleMode { NORMAL, PERIODIC, CLAMP }

	enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }

}
