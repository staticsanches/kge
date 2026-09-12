package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * The JVM decoder for a [URL] — file, classpath and http(s) URLs alike,
 * resolved by the platform URL machinery.
 *
 * The whole payload is read into a transient engine buffer, decoded through
 * [BytesDecoder], and the transient is released on every path. The blocking
 * stream read runs on [Dispatchers.IO]; the image decode runs on
 * [Dispatchers.Default] inside [BytesDecoder].
 */
object UrlDecoder : ImageService.Decoder<URL> {
    override suspend fun decode(
        data: URL,
        consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        data.openStream().use { it.readBytes() }.toEngineBuffer(data.toString()).use { read ->
            BytesDecoder.decode(read, consume)
        }
    }
}
