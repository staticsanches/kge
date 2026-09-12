package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The default encoder of a [Sprite] to PNG bytes in an engine buffer.
 *
 * PNG is lossless, so decoding the payload back on the same platform returns
 * the identical RGBA surface. The returned wrapper is caller-owned: close it to
 * release the buffer. Encoding is CPU-bound and runs on [Dispatchers.Default].
 */
object PngEncoder : ImageService.Encoder<ResourceWrapper<ByteBuffer>> {
    override suspend fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer> =
        withContext(Dispatchers.Default) { encodePngBytes(sprite).toEngineBuffer("PNG") }
}

/** Encodes [sprite]'s RGBA surface as PNG bytes on the current platform backend. */
internal expect fun encodePngBytes(sprite: Sprite): ByteArray

/** Wraps [this] in an engine buffer of the same size; the caller owns and must close it. */
internal fun ByteArray.toEngineBuffer(name: String): ResourceWrapper<ByteBuffer> =
    BufferService.allocate(size, name).letClosingIfFailed { wrapper ->
        val buffer = wrapper.resource
        for (i in indices) {
            buffer.put(i, this[i])
        }
        wrapper
    }

/** Reads all of [this]'s bytes in order; the inverse of [toEngineBuffer]. */
internal fun ByteBuffer.asByteArray(): ByteArray = ByteArray(capacity()) { get(it) }
