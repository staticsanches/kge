package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.pixelmap.buffer.*
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.spi.KGESPIExtensible
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream


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

/**
 * Default implementation of a [PixelBufferService] that uses the stb_image.h.
 */
@OptIn(KGESensitiveAPI::class)
internal class STBPixelBufferService : PixelBufferService {

	override fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> create(type: T, width: Int, height: Int): PB =
		MemFreeAction(type.expectedBufferCapacity(width, height)).closeIfFailed { memFreeAction ->
			val buffer = memFreeAction.buffer
			@Suppress("UNCHECKED_CAST")
			return@closeIfFailed when (val t: PixelBuffer.Type<PB, T> = type) {
				PixelBuffer.Type.RGBA -> RGBABuffer(width, height, buffer, memFreeAction)
				is PixelBuffer.Type.RGB -> RGBBuffer(width, height, t, buffer, memFreeAction)
				is PixelBuffer.Type.Grayscale -> GrayscaleBuffer(width, height, t, buffer, memFreeAction)
				is PixelBuffer.Type.Bitmap -> BitmapBuffer(width, height, t, buffer, memFreeAction)
			} as PB
		}

	override fun load(fileName: String): RGBABuffer = MemoryStack.stackPush().use { stack ->
		val width = stack.mallocInt(1)
		val height = stack.mallocInt(1)
		val components = stack.mallocInt(1)
		return@use STBFreeAction(
			STBImage.stbi_load(fileName, width, height, components, 4)
				?: throw RuntimeException("Unable to load $fileName")
		).closeIfFailed { stbFreeAction -> RGBABuffer(width[0], height[0], stbFreeAction.buffer, stbFreeAction) }
	}

	override fun load(url: URL): RGBABuffer = load(url::openStream)

	override fun load(isProvider: () -> InputStream): RGBABuffer =
		try {
			isProvider().use {
				val path = Files.createTempFile("image", ".tmp")
				try {
					it.transferTo(path.outputStream())
					return@use load(path.absolutePathString())
				} finally {
					path.deleteExisting()
				}
			}
		} catch (t: Throwable) {
			throw RuntimeException("Unable to load image", t)
		}

	override fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> duplicate(original: PB): PB =
		create(original.type, original.width, original.height).applyAndCloseIfFailed { copy ->
			MemoryUtil.memCopy(original.internalBuffer.clear(), copy.internalBuffer.clear())
		}

	override val servicePriority: Int
		get() = Int.MIN_VALUE

	private class MemFreeAction(size: Int) : AutoCloseable, KGECleanAction {

		val buffer: ByteBuffer = MemoryUtil.memAlloc(size)

		override fun invoke() = close()

		override fun close() = MemoryUtil.memFree(buffer.clear())

	}

	private class STBFreeAction(val buffer: ByteBuffer) : AutoCloseable, KGECleanAction {

		override fun invoke() = close()

		override fun close() = STBImage.stbi_image_free(buffer.clear())

	}

}
