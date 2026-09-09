package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.formatBytes
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import org.lwjgl.stb.STBIWriteCallback
import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import java.nio.channels.Channels

/**
 * JVM codec default: STB. Decode requests `comp = 4` so STB hands back the
 * R,G,B,A row-major bytes a [Sprite] stores; the returned native buffer is
 * wrapped as the sprite's storage (no copy) and freed by `stbi_image_free`
 * when the sprite closes. Encode writes through `stbi_write_png_to_func` into
 * an engine-allocated buffer.
 */
internal actual val pngServiceDefault: PngService = StbPngService

private object StbPngService : PngService {
    override fun decode(
        data: ByteBuffer,
        sampleMode: Pixmap.SampleMode,
        name: String?,
    ): Sprite {
        val width: Int
        val height: Int
        val image: ByteBuffer
        // STB reads from the buffer's current position and length is the
        // remaining bytes, but the engine buffer is absolute-addressable and
        // the web codec reads from the start — a duplicate reset to the start
        // keeps decode position-independent on both platforms.
        val input = data.duplicate().rewind()
        MemoryStack.stackPush().use { stack ->
            val outWidth = stack.mallocInt(1)
            val outHeight = stack.mallocInt(1)
            val components = stack.mallocInt(1)
            image =
                STBImage.stbi_load_from_memory(input, outWidth, outHeight, components, 4)
                    ?: throw IllegalArgumentException("not a decodable PNG (${data.capacity()} bytes)")
            width = outWidth[0]
            height = outHeight[0]
        }
        return wrapStbImage(image, width, height, sampleMode, name)
    }

    override fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer> {
        val pngBytes = ByteArrayOutputStream()
        val channel = Channels.newChannel(pngBytes)
        val written =
            STBImageWrite.stbi_write_png_to_func(
                { _, data, size -> channel.write(STBIWriteCallback.getData(data, size)) },
                MemoryUtil.NULL,
                sprite.width,
                sprite.height,
                4,
                sprite.byteBuffer,
                0,
            )
        check(written) { "unable to encode $sprite as PNG" }
        val bytes = pngBytes.toByteArray()
        return BufferService.allocate(bytes.size, "PNG").letClosingIfFailed { wrapper ->
            wrapper.resource.put(bytes)
            wrapper.resource.rewind()
            wrapper
        }
    }

    private fun wrapStbImage(
        image: ByteBuffer,
        width: Int,
        height: Int,
        sampleMode: Pixmap.SampleMode,
        name: String?,
    ): Sprite {
        val label =
            name?.let { "${formatBytes(width * height * Int.SIZE_BYTES)} ($it)" }
                ?: formatBytes(width * height * Int.SIZE_BYTES)
        val wrapper =
            ResourceWrapper(
                "byte buffer ($label)",
                // STB output is raw R,G,B,A bytes; sprite int reads are little-endian.
                image.order(ByteOrder.LITTLE_ENDIAN),
                KGECleanAction { STBImage.stbi_image_free(image) },
            )
        return wrapper.letClosingIfFailed { Sprite(width, height, it, sampleMode, name) }
    }
}
