package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * The JVM decoder for a [URL] — file, classpath and http(s) alike.
 *
 * The blocking read runs on [Dispatchers.IO]; the payload is decoded from a
 * transient buffer via [BytesDecoder], released on every path.
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
