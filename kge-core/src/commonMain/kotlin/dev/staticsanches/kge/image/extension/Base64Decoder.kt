package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/**
 * The default decoder for a base64-encoded image payload — the portable
 * download / data-URL representation.
 *
 * [data] is decoded into a transient engine buffer, decoded through
 * [BytesDecoder], and the transient is released on every path; the [String]
 * payload itself is not a resource. An invalid base64 string throws before any
 * image decode runs. Decoding is CPU-bound and runs on [Dispatchers.Default].
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
