package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.MemoryAllocatorService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlin.io.encoding.Base64

/**
 * A typed source of PNG bytes — the S5 load boundary.
 *
 * [read] materializes the whole payload and hands it over with an owner: the
 * returned [ResourceWrapper] is a live allocation the caller must close on
 * every path. The interface is open — a new source format is a new [PngSource]
 * implementation, with no codec change. Only [read] is suspend: materializing
 * a source is inherently async on the web (fetch), while the codec itself
 * ([PngService]) is synchronous.
 *
 * The engine-provided sources are the companion factories: [base64] lives in
 * the common module, and each platform adds its own URL source next to it
 * (a `java.net.URL` on the JVM, a fetch of a URL string on the web), so the
 * platform machinery never leaks into the common module.
 */
interface PngSource {
    /** Materializes the source bytes; the caller owns and must close the returned wrapper. */
    suspend fun read(): ResourceWrapper<ByteBuffer>

    companion object {
        /**
         * A [PngSource] over a base64-encoded PNG payload — the portable
         * download / data-URL representation. The payload is decoded at
         * [read] time; an invalid base64 string throws.
         */
        fun base64(base64Payload: String): PngSource =
            object : PngSource {
                override suspend fun read(): ResourceWrapper<ByteBuffer> =
                    Base64.Default.decode(base64Payload).toEngineBuffer()
            }
    }
}

/** Wraps [bytes] in an engine buffer of the same size; the caller owns and must close it. */
internal fun ByteArray.toEngineBuffer(name: String? = null): ResourceWrapper<ByteBuffer> =
    MemoryAllocatorService.allocate(size, name).letClosingIfFailed { wrapper ->
        val buffer = wrapper.resource
        for (i in indices) {
            buffer.put(i, this[i])
        }
        wrapper
    }
