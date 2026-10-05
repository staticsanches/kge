package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed

/** One decoded font payload, shared by every native face built over it. */
internal class TtfPayload(
    bytes: ByteArray,
) : KGEResource {
    /** The one engine buffer every face over this payload maps or copies from. */
    internal val buffer: ResourceWrapper<ByteBuffer> =
        BufferService.allocate(bytes.size, "font").applyClosingIfFailed {
            val target = resource
            for (index in bytes.indices) target.put(index, bytes[index])
        }

    override fun close() {
        buffer.close()
    }
}
