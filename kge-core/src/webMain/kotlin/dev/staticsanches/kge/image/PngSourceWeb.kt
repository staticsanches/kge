package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * A [PngSource] over a URL, fetched through the platform `fetch` — http(s)
 * and data: URLs alike. The returned bytes are an engine-buffer allocation
 * the caller owns.
 */
fun PngSource.Companion.fetch(url: String): PngSource =
    object : PngSource {
        override suspend fun read(): ResourceWrapper<ByteBuffer> = fetchBytes(url).toEngineBuffer()
    }

private suspend fun fetchBytes(url: String): ByteArray {
    val buffer = fetch(url).await().arrayBuffer().await()
    val view = DataView(buffer)
    return ByteArray(Uint8Array(buffer).length) { view.getUint8(it) }
}

/** The platform `fetch` and `Response` globals (node undici and browsers both provide them). */
external fun fetch(url: String): Promise<Response>

external class Response : JsAny {
    fun arrayBuffer(): Promise<ArrayBuffer>
}
