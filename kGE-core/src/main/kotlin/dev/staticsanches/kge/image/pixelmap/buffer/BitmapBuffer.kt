package dev.staticsanches.kge.image.pixelmap.buffer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer.Type
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGECleaner
import java.nio.ByteBuffer

/**
 * [PixelBuffer] associated with [Type.Bitmap].
 */
@OptIn(KGESensitiveAPI::class)
class BitmapBuffer(
	width: Int, height: Int, type: Type.Bitmap, buffer: ByteBuffer, bufferCleanAction: KGECleanAction
) : PixelBuffer<Type.Bitmap>(width, height, type, buffer) {

	private val cleanable = KGECleaner.registerLeakDetector(this, representation, bufferCleanAction)

	private val foregroundRGB = PixelService.toRGB(type.foreground, type.matteBackground)
	private val backgroundRGB = PixelService.toRGB(type.background, type.matteBackground)

	private val pitch = type.expectedAbsolutePitch(width)

	override fun uncheckedGet(x: Int, y: Int): Pixel =
		if (getBit(x, y)) type.foreground else type.background

	override fun uncheckedSet(x: Int, y: Int, pixel: Pixel): Boolean {
		val index = getIndex(x, y)
		this[index] = internalBuffer[index].toInt() or pixel.toBitMask(x)
		return true
	}

	override fun clear(pixel: Pixel) {
		val newValue: Byte = (if (pixel.toBitMask(0) > 0) 0xFF else 0x00).toByte()
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
					byte = byte or pixelByXY(x, y).toBitMask(x++)
				}
				this[getIndex(blockStart, y)] = byte
			}
		}
	}

	private fun getIndex(x: Int, y: Int): Int =
		if (type.negativePitch) (height - y) * pitch + x / 8
		else y * pitch + x / 8

	private fun getBit(x: Int, y: Int): Boolean =
		internalBuffer[getIndex(x, y)].toInt() and getBitMask(x) > 0

	private fun getBitMask(x: Int): Int = 1 shl (7 - x % 8)

	private operator fun set(index: Int, byte: Int) =
		internalBuffer.put(index, byte.toByte())

	/**
	 * For a rgb [Pixel], calculate the distance to [foregroundRGB] and [backgroundRGB],
	 * returning true if it is closer to the [foregroundRGB].
	 */
	private fun Pixel.toBitMask(x: Int): Int {
		val rgb = PixelService.toRGB(this, type.matteBackground)
		val foregroundDistance = PixelService.distance2(rgb, foregroundRGB)
		val backgroundDistance = PixelService.distance2(rgb, backgroundRGB)
		return if (foregroundDistance <= backgroundDistance) getBitMask(x) else 0
	}

	override fun close() = cleanable.clean()

}
