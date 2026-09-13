@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.resource.ResourceWrapper
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * The browser decoder for a URL, fetched through the platform `fetch` —
 * http(s) and data: URLs alike.
 *
 * `fetch` is non-blocking, so no dispatcher is needed; the payload is decoded
 * from a transient buffer via [BytesDecoder], released on every path.
 */
object FetchDecoder : ImageService.Decoder<String> {
    override suspend fun decode(
        data: String,
        consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
    ) {
        fetchBytes(data).toEngineBuffer("fetch image").use { fetched ->
            BytesDecoder.decode(fetched, consume)
        }
    }
}

private suspend fun fetchBytes(url: String): ByteArray {
    val buffer = fetch(url).await().arrayBuffer().await()
    val view = DataView(buffer)
    return ByteArray(Uint8Array(buffer).length) { view.getUint8(it) }
}

/** The browser `fetch` and `Response` globals. */
external fun fetch(url: String): Promise<Response>

external class Response : JsAny {
    fun arrayBuffer(): Promise<ArrayBuffer>
}
