package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.image.pixelmap.buffer.BitmapBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.GrayscaleBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import dev.staticsanches.kge.image.pixelmap.buffer.RGBBuffer
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.OffHeapByteBuffer
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.resource.invokeIfFailed
import dev.staticsanches.kge.resource.use
import dev.staticsanches.kge.spi.KGESPIExtensible
import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImageWrite.stbi_write_png
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer

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
private data object STBPixelBufferService : PixelBufferService {
    override fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> create(
        type: T,
        width: Int,
        height: Int,
    ): PB =
        OffHeapByteBuffer(type.expectedBufferCapacity(width, height)).invokeIfFailed { (buffer, cleanAction) ->
            @Suppress("UNCHECKED_CAST")
            when (val t: PixelBuffer.Type<PB, T> = type) {
                PixelBuffer.Type.RGBA -> RGBABuffer(width, height, buffer, cleanAction)
                is PixelBuffer.Type.RGB -> RGBBuffer(width, height, t, buffer, cleanAction)
                is PixelBuffer.Type.Grayscale -> GrayscaleBuffer(width, height, t, buffer, cleanAction)
                is PixelBuffer.Type.Bitmap -> BitmapBuffer(width, height, t, buffer, cleanAction)
            } as PB
        }

    override fun load(fileName: String): RGBABuffer =
        MemoryStack.stackPush().use { stack ->
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            val components = stack.mallocInt(1)
            return@use STBBuffer(
                STBImage.stbi_load(fileName, width, height, components, 4)
                    ?: throw RuntimeException("Unable to load $fileName"),
            ).invokeIfFailed { stbBuffer -> RGBABuffer(width[0], height[0], stbBuffer.buffer, stbBuffer) }
        }

    override fun load(url: URL): RGBABuffer = load(url::openStream)

    override fun load(isProvider: () -> InputStream): RGBABuffer =
        MemoryStack.stackPush().use { stack ->
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            val components = stack.mallocInt(1)

            OffHeapByteBuffer(isProvider).use { (buffer) ->
                STBBuffer(
                    STBImage.stbi_load_from_memory(buffer, width, height, components, 4)
                        ?: throw RuntimeException("Unable to load image"),
                ).invokeIfFailed { stbBuffer -> RGBABuffer(width[0], height[0], stbBuffer.buffer, stbBuffer) }
            }
        }

    override fun <PB : PixelBuffer<PB, T>, T : PixelBuffer.Type<PB, T>> duplicate(original: PB): PB =
        create(original.type, original.width, original.height).applyAndCloseIfFailed { copy ->
            MemoryUtil.memCopy(original.internalBuffer.clear(), copy.internalBuffer.clear())
        }

    override fun writePNG(
        fileName: String,
        buffer: RGBABuffer,
    ): Boolean =
        stbi_write_png(fileName, buffer.width, buffer.height, 4, buffer.internalBuffer.clear(), buffer.width * 4)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

@JvmInline
private value class STBBuffer(
    val buffer: ByteBuffer,
) : KGECleanAction {
    override fun invoke() = STBImage.stbi_image_free(buffer.clear())
}
