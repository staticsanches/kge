package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.formatBytes
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import java.nio.ByteOrder

/**
 * JVM backend: STB with `req_comp = 4`, so every supported input format
 * (PNG/JPEG/TGA/BMP/…) comes back as row-major R,G,B,A bytes. The native buffer
 * STB allocates is wrapped as an engine buffer the decoder owns until `consume`
 * hands it over; `STBImage.stbi_image_free` is the wrapper's clean action, so
 * the consumer frees it on close. STB reads from the buffer's current position,
 * so a `duplicate().rewind()` view keeps decode independent of the caller's
 * `java.nio` position.
 */
internal actual suspend fun decodeImageBytes(
    source: ByteBuffer,
    consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
) {
    val input = source.duplicate().rewind()
    MemoryStack.stackPush().use { stack ->
        val outWidth = stack.mallocInt(1)
        val outHeight = stack.mallocInt(1)
        val components = stack.mallocInt(1)
        val image =
            STBImage.stbi_load_from_memory(input, outWidth, outHeight, components, 4)
                ?: throw IllegalArgumentException("not a decodable image (${source.capacity()} bytes)")
        val wrapper = wrapStbImage(image, outWidth[0], outHeight[0])
        consume(outWidth[0], outHeight[0], wrapper)
    }
}

/**
 * Wraps STB's [image] as an engine buffer: it is freed by `stbi_image_free`
 * when the wrapper closes, and freed here directly when the wrapper cannot be
 * constructed — between the STB allocation and the ownership hand-off.
 */
private fun wrapStbImage(
    image: ByteBuffer,
    width: Int,
    height: Int,
): ResourceWrapper<ByteBuffer> {
    val label = formatBytes(width * height * Int.SIZE_BYTES)
    return try {
        ResourceWrapper(
            "byte buffer ($label)",
            // STB output is raw R,G,B,A bytes; engine int reads are little-endian.
            image.order(ByteOrder.LITTLE_ENDIAN),
            KGECleanAction { STBImage.stbi_image_free(image) },
        )
    } catch (e: Throwable) {
        STBImage.stbi_image_free(image)
        throw e
    }
}
