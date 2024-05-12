package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.image.pixelmap.buffer.BitmapBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.GrayscaleBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import dev.staticsanches.kge.image.pixelmap.buffer.RGBBuffer
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.MemFreeAction
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.spi.KGESPIExtensible
import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImageWrite.stbi_write_png
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
    fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> create(
        type: T,
        width: Int,
        height: Int,
    ): PB

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

    /**
     * Writes the [buffer] content to a PNG file.
     */
    fun writePNG(
        fileName: String,
        buffer: RGBABuffer,
    ): Boolean

    companion object : PixelBufferService by KGESPIExtensible.getOptionalWithHigherPriority() ?: STBPixelBufferService
}

/**
 * Default implementation of a [PixelBufferService] that uses the stb_image.h.
 */
internal data object STBPixelBufferService : PixelBufferService {
    override fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> create(
        type: T,
        width: Int,
        height: Int,
    ): PB =
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

    override fun load(fileName: String): RGBABuffer =
        MemoryStack.stackPush().use { stack ->
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            val components = stack.mallocInt(1)
            return@use STBFreeAction(
                STBImage.stbi_load(fileName, width, height, components, 4)
                    ?: throw RuntimeException("Unable to load $fileName"),
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

    override fun writePNG(
        fileName: String,
        buffer: RGBABuffer,
    ): Boolean = stbi_write_png(fileName, buffer.width, buffer.height, 4, buffer.internalBuffer.clear(), buffer.width * 4)

    override val servicePriority: Int
        get() = Int.MIN_VALUE

    private class STBFreeAction(val buffer: ByteBuffer) : AutoCloseable, KGECleanAction {
        override fun invoke() = close()

        override fun close() = STBImage.stbi_image_free(buffer.clear())
    }
}
