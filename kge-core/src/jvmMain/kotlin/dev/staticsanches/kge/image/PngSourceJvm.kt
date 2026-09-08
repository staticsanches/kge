package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * A [PngSource] over a [java.net.URL] — file, classpath and http(s) URLs
 * alike, resolved by the platform URL machinery. The blocking stream I/O of
 * [read] is dispatched off the caller on [Dispatchers.IO]; the returned bytes
 * are an engine-buffer allocation the caller owns.
 */
fun PngSource.Companion.url(url: URL): PngSource =
    object : PngSource {
        override suspend fun read(): ResourceWrapper<ByteBuffer> =
            withContext(Dispatchers.IO) {
                url.openStream().use { it.readBytes() }.toEngineBuffer()
            }
    }
