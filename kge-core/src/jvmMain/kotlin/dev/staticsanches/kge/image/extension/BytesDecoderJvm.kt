package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.formatBytes
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import java.nio.ByteOrder

/**
 * JVM backend: STB with `req_comp = 4`, so every supported format decodes to
 * row-major R,G,B,A. The result is handed to `consume`, which owns the buffer
 * and frees it on close. Decoding reads a rewound duplicate, so the caller's
 * `java.nio` position is untouched.
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
