package dev.staticsanches.kge.image.pixelmap.buffer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer.Type
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGECleaner
import java.nio.ByteBuffer

/**
 * [PixelBuffer] associated with [Type.Grayscale].
 */
@OptIn(KGESensitiveAPI::class)
class GrayscaleBuffer(
	width: Int, height: Int, type: Type.Grayscale, buffer: ByteBuffer, bufferCleanAction: KGECleanAction
) : PixelBuffer<Type.Grayscale>(width, height, type, buffer) {

	private val cleanable = KGECleaner.registerLeakDetector(this, representation, bufferCleanAction)

	override fun uncheckedGet(x: Int, y: Int): Pixel =
		PixelService.fromGrayscale(internalBuffer.get(y * width + x).toUByte(), type.defaultAlpha)

	override fun uncheckedSet(x: Int, y: Int, pixel: Pixel): Boolean {
		internalBuffer.put(y * width + x, PixelService.toGrayscale(pixel).toByte())
		return true
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
