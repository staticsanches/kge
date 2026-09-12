package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The default decoder for an engine buffer of encoded image bytes.
 *
 * The payload format is auto-detected by the platform backend (STB on the
 * JVM, `createImageBitmap` on the web), so the same decoder reads every format
 * that backend supports. [data] is caller-owned and is not closed here; the
 * backend allocates the RGBA buffer it hands to `consume` and transfers
 * ownership on that call — the consumer closes it from then on.
 */
object BytesDecoder : ImageService.Decoder<ResourceWrapper<ByteBuffer>> {
    override suspend fun decode(
        data: ResourceWrapper<ByteBuffer>,
        consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
    ) = withContext(Dispatchers.Default) {
        decodeImageBytes(data.resource, consume)
    }
}

/**
 * Decodes [source]'s encoded image bytes on the current platform backend,
 * passing the RGBA surface to [consume]. The backend owns the pixels wrapper
 * until it invokes [consume]; that call transfers ownership to the consumer,
 * which closes it.
 */
internal expect suspend fun decodeImageBytes(
    source: ByteBuffer,
    consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
)
