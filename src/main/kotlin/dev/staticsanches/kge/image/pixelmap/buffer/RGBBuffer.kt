package dev.staticsanches.kge.image.pixelmap.buffer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer.Type
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGELeakDetector
import java.nio.ByteBuffer

/**
 * [PixelBuffer] associated with [Type.RGB].
 */
@OptIn(KGESensitiveAPI::class)
class RGBBuffer(width: Int, height: Int, type: Type.RGB, buffer: ByteBuffer, bufferCleanAction: KGECleanAction) :
	PixelBuffer<RGBBuffer, Type.RGB>(width, height, type, buffer) {

	private val cleanable = KGELeakDetector.register(this, representation, bufferCleanAction)

	override fun uncheckedGet(x: Int, y: Int): Pixel =
		Pixel.rgba(
			internalBuffer.position((y * width + x) * 3).get().toInt(),
			internalBuffer.get().toInt(),
			internalBuffer.get().toInt(),
			type.defaultAlpha
		)

	override fun uncheckedSet(x: Int, y: Int, pixel: Pixel): Boolean {
		val (r, g, b) = PixelService.toRGB(pixel, type.matteBackground)
		internalBuffer
			.position((y * width + x) * 3)
			.put(r.toByte())
			.put(g.toByte())
			.put(b.toByte())
		return true
	}

	override fun clear(pixel: Pixel) {
		val (r, g, b) = PixelService.toRGB(pixel, type.matteBackground)
		internalBuffer.clear()
		while (internalBuffer.hasRemaining()) {
			internalBuffer
				.put(r.toByte())
				.put(g.toByte())
				.put(b.toByte())
		}
	}

	override fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
		internalBuffer.clear()
		for (y in 0..<height) {
			for (x in 0..<width) {
				val (r, g, b) = PixelService.toRGB(pixelByXY(x, y), type.matteBackground)
				internalBuffer
					.put(r.toByte())
					.put(g.toByte())
					.put(b.toByte())
			}
		}
	}

	override fun close() = cleanable.clean()

}
