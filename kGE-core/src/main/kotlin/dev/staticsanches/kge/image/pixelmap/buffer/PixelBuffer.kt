package dev.staticsanches.kge.image.pixelmap.buffer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer.Type
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.types.vector.Int2D
import dev.staticsanches.kge.types.vector.by
import dev.staticsanches.kge.utils.humanReadableByteCountBin
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * [ByteBuffer] wrapper to allow it to function as a [PixelMap].
 */
@OptIn(KGESensitiveAPI::class)
sealed class PixelBuffer<T : Type>(
	final override inline val width: Int,
	final override inline val height: Int,
	val type: T,
	@KGESensitiveAPI
	val internalBuffer: ByteBuffer
) : PixelMap, KGEResource {

	init {
		check(width > 0 && height > 0) { "Invalid buffer dimension ${width}x$height" }

		val expectedBufferCapacity = type.expectedBufferCapacity(width, height)
		check(expectedBufferCapacity == internalBuffer.capacity()) {
			"Invalid buffer capacity. Expected: ${expectedBufferCapacity}. Actual: ${internalBuffer.capacity()}"
		}

		check(internalBuffer.order() == ByteOrder.nativeOrder()) {
			"Invalid order for the buffer. It must use the native order: ${ByteOrder.nativeOrder()}"
		}
	}

	final override val size: Int2D = width by height

	protected val representation =
		"${type::class.java.simpleName} ${width}x$height (${
			humanReadableByteCountBin(internalBuffer.capacity().toLong())
		})"

	override fun toString(): String = representation

	/**
	 * Available [PixelBuffer] types.
	 */
	sealed interface Type {

		/**
		 * Represents images with 4-channels.
		 *
		 * @see RGBABuffer
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
		 *
		 * @see RGBBuffer
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
		 *
		 * @see GrayscaleBuffer
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
		 *
		 * @see BitmapBuffer
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

}
