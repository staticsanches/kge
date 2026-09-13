package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/**
 * The default decoder for a base64-encoded image payload.
 *
 * [data] is decoded through [BytesDecoder] with a transient engine buffer that
 * is released on every path; the [String] payload is not a resource. Invalid
 * base64 throws before any image decode runs. Runs on [Dispatchers.Default].
 */
object Base64Decoder : ImageService.Decoder<String> {
    override suspend fun decode(
        data: String,
        consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
    ) = withContext(Dispatchers.Default) {
        Base64.Default.decode(data).toEngineBuffer("base64 image").use { decoded ->
            BytesDecoder.decode(decoded, consume)
        }
    }
}
