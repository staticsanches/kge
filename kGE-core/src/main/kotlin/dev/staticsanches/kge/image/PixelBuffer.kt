@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGECleaner
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.types.vector.Int2D
import dev.staticsanches.kge.types.vector.by
import dev.staticsanches.kge.utils.humanReadableByteCountBin
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * Wrapper to allow store and retrieve [Pixel]s inside a [ByteBuffer] for a specific [Sprite.Type].
 *
 * It is logically a 2D matrix of [Pixel]s with a "row major" configuration and "direct access"
 * by coordinate (x, y).
 *
 * The coordinates (0, 0) indicates the top-left corner and ([width] - 1, [height] - 1)
 * indicates the bottom-right corner.
 */
@OptIn(KGESensitiveAPI::class)
sealed class PixelBuffer<T : Sprite.Type> private constructor(
	val width: Int, val height: Int, val type: T,
	@KGESensitiveAPI
	val internalBuffer: ByteBuffer
) : KGEResource, Sequence<Pixel> {

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

	val size: Int2D = width by height

	operator fun get(x: Int, y: Int): Pixel {
		checkCoordinates(x, y)
		return uncheckedGet(x, y)
	}

	operator fun set(x: Int, y: Int, pixel: Pixel) {
		checkCoordinates(x, y)
		uncheckedSet(x, y, pixel)
	}

	abstract fun uncheckedGet(x: Int, y: Int): Pixel
	abstract fun uncheckedSet(x: Int, y: Int, pixel: Pixel)

	abstract fun clear(pixel: Pixel)
	abstract fun clear(pixelByXY: (x: Int, y: Int) -> Pixel)

	override fun iterator(): Iterator<Pixel> =
		iterator {
			for (y in 0..<height) {
				for (x in 0..<width) {
					yield(uncheckedGet(x, y))
				}
			}
		}

	private fun checkCoordinates(x: Int, y: Int) {
		if (x < 0 || x > width || y < 0 || y > height) {
			throw IndexOutOfBoundsException("Coordinates ($x, $y) does not comply to 0 <= x < $width and 0 <= y < $height")
		}
	}

	protected val representation =
		"${type::class.java.simpleName} ${width}x$height (${
			humanReadableByteCountBin(internalBuffer.capacity().toLong())
		})"

	override fun toString(): String = representation

	/**
	 * [PixelBuffer] associated to [Sprite.Type.RGBA].
	 */
	class RGBA(width: Int, height: Int, buffer: ByteBuffer, bufferCleanAction: KGECleanAction) :
		PixelBuffer<Sprite.Type.RGBA>(width, height, Sprite.Type.RGBA, buffer) {

		private val cleanable = KGECleaner.registerLeakDetector(this, representation, bufferCleanAction)

		override fun uncheckedGet(x: Int, y: Int): Pixel =
			Pixel(internalBuffer.getInt((y * width + x) * 4))

		override fun uncheckedSet(x: Int, y: Int, pixel: Pixel) {
			internalBuffer.putInt((y * width + x) * 4, pixel.nativeRGBA)
		}

		override fun clear(pixel: Pixel) {
			internalBuffer.clear()
			while (internalBuffer.hasRemaining()) {
				internalBuffer.putInt(pixel.nativeRGBA)
			}
		}

		override fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
			internalBuffer.clear()
			for (y in 0..<height) {
				for (x in 0..<width) {
					internalBuffer.putInt(pixelByXY(x, y).nativeRGBA)
				}
			}
		}

		override fun close() = cleanable.clean()

	}

	/**
	 * [PixelBuffer] associated to [Sprite.Type.RGB].
	 */
	class RGB(width: Int, height: Int, type: Sprite.Type.RGB, buffer: ByteBuffer, bufferCleanAction: KGECleanAction) :
		PixelBuffer<Sprite.Type.RGB>(width, height, type, buffer) {

		private val cleanable = KGECleaner.registerLeakDetector(this, representation, bufferCleanAction)

		override fun uncheckedGet(x: Int, y: Int): Pixel = Pixel(
			internalBuffer.position((y * width + x) * 3).get().toUByte(),
			internalBuffer.get().toUByte(),
			internalBuffer.get().toUByte(),
			type.defaultAlpha
		)

		override fun uncheckedSet(x: Int, y: Int, pixel: Pixel) {
			val rgbPixel = PixelService.toRGB(pixel, type.matteBackground)
			internalBuffer
				.position((y * width + x) * 3)
				.put(rgbPixel.r.toByte())
				.put(rgbPixel.g.toByte())
				.put(rgbPixel.b.toByte())
		}

		override fun clear(pixel: Pixel) {
			val rgbPixel = PixelService.toRGB(pixel, type.matteBackground)
			val r = rgbPixel.r.toByte()
			val g = rgbPixel.g.toByte()
			val b = rgbPixel.b.toByte()
			internalBuffer.clear()
			while (internalBuffer.hasRemaining()) {
				internalBuffer.put(r).put(g).put(b)
			}
		}

		override fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
			internalBuffer.clear()
			for (y in 0..<height) {
				for (x in 0..<width) {
					val pixel = PixelService.toRGB(pixelByXY(x, y), type.matteBackground)
					internalBuffer
						.put(pixel.r.toByte())
						.put(pixel.g.toByte())
						.put(pixel.b.toByte())
				}
			}
		}

		override fun close() = cleanable.clean()

	}

	/**
	 * [PixelBuffer] associated to [Sprite.Type.Grayscale].
	 */
	class Grayscale(
		width: Int, height: Int, type: Sprite.Type.Grayscale, buffer: ByteBuffer, bufferCleanAction: KGECleanAction
	) : PixelBuffer<Sprite.Type.Grayscale>(width, height, type, buffer) {

		private val cleanable = KGECleaner.registerLeakDetector(this, representation, bufferCleanAction)

		override fun uncheckedGet(x: Int, y: Int): Pixel =
			PixelService.fromGrayscale(internalBuffer.get(y * width + x).toUByte(), type.defaultAlpha)

		override fun uncheckedSet(x: Int, y: Int, pixel: Pixel) {
			internalBuffer.put(y * width + x, PixelService.toGrayscale(pixel).toByte())
		}

		override fun clear(pixel: Pixel) {
			val grayscale = PixelService.toGrayscale(pixel).toByte()
			internalBuffer.clear()
			while (internalBuffer.hasRemaining()) {
				internalBuffer.put(grayscale)
			}
		}

		override fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
			internalBuffer.clear()
			for (y in 0..<height) {
				for (x in 0..<width) {
					val pixel = pixelByXY(x, y)
					internalBuffer.put(PixelService.toGrayscale(pixel).toByte())
				}
			}
		}

		override fun close() = cleanable.clean()

	}

	/**
	 * [PixelBuffer] associated to [Sprite.Type.Bitmap].
	 */
	class Bitmap(
		width: Int, height: Int, type: Sprite.Type.Bitmap, buffer: ByteBuffer, bufferCleanAction: KGECleanAction
	) : PixelBuffer<Sprite.Type.Bitmap>(width, height, type, buffer) {

		private val cleanable = KGECleaner.registerLeakDetector(this, representation, bufferCleanAction)

		private val foregroundRGB = PixelService.toRGB(type.foreground, type.matteBackground)
		private val backgroundRGB = PixelService.toRGB(type.background, type.matteBackground)

		private val pitch = type.expectedAbsolutePitch(width)

		override fun uncheckedGet(x: Int, y: Int): Pixel =
			if (getBit(x, y)) type.foreground
			else type.background

		override fun uncheckedSet(x: Int, y: Int, pixel: Pixel) {
			val index = getIndex(x, y)
			this[index] = internalBuffer[index].toInt() or pixel.toMaskedByte(x)
		}

		override fun clear(pixel: Pixel) {
			val newValue: Byte = (if (pixel.toMaskedByte(0) > 0) 0xFF else 0x00).toByte()
			internalBuffer.clear()
			while (internalBuffer.hasRemaining()) {
				internalBuffer.put(newValue)
			}
		}

		override fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
			for (y in 0..<height) {
				for (blockStart in 0..<8 * pitch step 8) {
					var byte = 0
					var x = blockStart
					for (index in 0..<8) {
						if (x == width) break
						byte = byte or pixelByXY(x, y).toMaskedByte(x++)
					}
					this[getIndex(blockStart, y)] = byte
				}
			}
		}

		private fun getIndex(x: Int, y: Int): Int =
			if (type.negativePitch) (height - y) * pitch + x / 8
			else y * pitch + x / 8

		private fun getBit(x: Int, y: Int): Boolean =
			internalBuffer[getIndex(x, y)].toInt() and getMaskedByte(x) > 0

		private fun getMaskedByte(x: Int): Int =
			if (isLittleEndian) 1 shl (7 - x % 8)
			else 1 shr x % 8

		private operator fun set(index: Int, byte: Int) =
			if (isLittleEndian) internalBuffer.put(index, (byte shr 24).toByte())
			else internalBuffer.put(index, byte.toByte())

		/**
		 * For a rgb [Pixel], calculate the distance to [foregroundRGB] and [backgroundRGB],
		 * returning true if it is closer to the [foregroundRGB].
		 */
		private fun Pixel.toMaskedByte(x: Int): Int {
			val rgb = PixelService.toRGB(this, type.matteBackground)
			val foregroundDistance = PixelService.distance2(rgb, foregroundRGB)
			val backgroundDistance = PixelService.distance2(rgb, backgroundRGB)
			return if (foregroundDistance <= backgroundDistance) getMaskedByte(x) else 0
		}

		override fun close() = cleanable.clean()

		private companion object {

			private val isLittleEndian = ByteOrder.LITTLE_ENDIAN == ByteOrder.nativeOrder()

		}

	}

}
