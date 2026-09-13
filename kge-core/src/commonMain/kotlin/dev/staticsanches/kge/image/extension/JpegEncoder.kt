package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The default encoder of a [Sprite] to JPEG bytes in an engine buffer.
 *
 * JPEG is lossy: the payload round-trips dimensions but not exact pixels. The
 * returned wrapper is caller-owned and must be closed. Runs on
 * [Dispatchers.Default].
 */
object JpegEncoder : ImageService.Encoder<ResourceWrapper<ByteBuffer>> {
    override suspend fun encode(sprite: Sprite): ResourceWrapper<ByteBuffer> =
        withContext(Dispatchers.Default) { encodeJpegBytes(sprite).toEngineBuffer("JPEG") }
}

/** Encodes [sprite]'s RGBA surface as JPEG bytes on the current platform backend. */
internal expect fun encodeJpegBytes(sprite: Sprite): ByteArray
