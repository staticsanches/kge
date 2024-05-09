package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import dev.staticsanches.kge.spi.KGESPIExtensible
import java.io.InputStream
import java.net.URL


interface PixelBufferService : KGESPIExtensible {

	/**
	 * Creates a new uninitialized [PixelBuffer].
	 */
	fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> create(type: T, width: Int, height: Int): PB

	/**
	 * Loads the image content in a [RGBABuffer].
	 */
	fun load(fileName: String): RGBABuffer

	/**
	 * Loads the image content in a [RGBABuffer].
	 */
	fun load(url: URL): RGBABuffer

	/**
	 * Loads the image content in a [RGBABuffer].
	 */
	fun load(isProvider: () -> InputStream): RGBABuffer

	/**
	 * Creates a copy of the informed buffer.
	 */
	fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> duplicate(original: PB): PB

	companion object : PixelBufferService by KGESPIExtensible.getWithHigherPriority()

}
