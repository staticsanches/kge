package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * Owns the [ByteBuffer] backends: allocation and the bulk copy/fill of whole
 * int regions.
 *
 * Allocation is an extension capability of the engine: the platform default
 * allocates off-heap memory (LWJGL on JVM, a `TypedArray` on the web) and a
 * consumer may replace it for the whole process via
 * [override][KGEOverridable.Proxy.override] — e.g. an alternate backend.
 *
 * [fillInts]/[copyInts] own the whole operation, fallback included: their
 * default bodies here are the portable loop (memmove-safe overlap), and a
 * platform implementation overrides only when it has a faster native path —
 * taking it when its conditions hold and calling `super` (the fallback)
 * otherwise. The companion validates the range first, so every implementation
 * only ever sees a legal region.
 */
interface BufferService : KGEOverridable {
    /**
     * Allocates a buffer of [sizeInBytes] bytes with unspecified content,
     * owned by the caller: close the returned wrapper to release the memory,
     * and never use its [ResourceWrapper.resource] after close.
     *
     * [name] identifies the allocation in the wrapper's leak/fail-fast
     * messages — a diagnostic label, nullable for anonymous buffers.
     */
    fun allocate(
        sizeInBytes: Int,
        name: String? = null,
    ): ResourceWrapper<ByteBuffer>

    /**
     * Fills [count] int slots (4 bytes each) of [target] with [value], the
     * first at byte offset [fromByteOffset]. The default body is the portable
     * loop; platform implementations override it with a native fill when one
     * is available and call `super` otherwise.
     */
    fun fillInts(
        target: ByteBuffer,
        fromByteOffset: Int,
        count: Int,
        value: Int,
    ) {
        for (i in 0 until count) {
            target.putInt(fromByteOffset + i * Int.SIZE_BYTES, value)
        }
    }

    /**
     * Copies [count] ints from [source] at byte offset [sourceFromByteOffset]
     * into [dst] at byte offset [dstFromByteOffset], memmove-safe for
     * overlapping regions of one buffer. The default body is the portable
     * memmove loop; platform implementations override it with a native copy
     * when one is available and call `super` otherwise.
     */
    fun copyInts(
        dst: ByteBuffer,
        dstFromByteOffset: Int,
        source: ByteBuffer,
        sourceFromByteOffset: Int,
        count: Int,
    ) {
        // memmove: when the destination starts past the source, iterate
        // backward so an int is read before a write could overwrite it.
        if (source === dst && dstFromByteOffset > sourceFromByteOffset) {
            for (i in count - 1 downTo 0) {
                dst.putInt(
                    dstFromByteOffset + i * Int.SIZE_BYTES,
                    source.getInt(sourceFromByteOffset + i * Int.SIZE_BYTES),
                )
            }
        } else {
            for (i in 0 until count) {
                dst.putInt(
                    dstFromByteOffset + i * Int.SIZE_BYTES,
                    source.getInt(sourceFromByteOffset + i * Int.SIZE_BYTES),
                )
            }
        }
    }

    companion object :
        KGEOverridable.Proxy<BufferService>(BufferService::class, bufferServiceDefault),
        BufferService {
        override fun allocate(
            sizeInBytes: Int,
            name: String?,
        ): ResourceWrapper<ByteBuffer> {
            require(sizeInBytes >= 0) { "sizeInBytes must be >= 0: $sizeInBytes" }
            return delegate.allocate(sizeInBytes, name)
        }

        override fun fillInts(
            target: ByteBuffer,
            fromByteOffset: Int,
            count: Int,
            value: Int,
        ) {
            target.requireRange(fromByteOffset, count)
            if (count == 0) return
            delegate.fillInts(target, fromByteOffset, count, value)
        }

        override fun copyInts(
            dst: ByteBuffer,
            dstFromByteOffset: Int,
            source: ByteBuffer,
            sourceFromByteOffset: Int,
            count: Int,
        ) {
            dst.requireRange(dstFromByteOffset, count)
            source.requireRange(sourceFromByteOffset, count)
            if (count == 0) return
            delegate.copyInts(dst, dstFromByteOffset, source, sourceFromByteOffset, count)
        }
    }
}

internal expect val bufferServiceDefault: BufferService

/** Throws [IndexOutOfBoundsException] unless [count] ints fit at [fromByteOffset] of [buffer]. */
internal fun ByteBuffer.requireRange(
    fromByteOffset: Int,
    count: Int,
) {
    val end = fromByteOffset.toLong() + count.toLong() * Int.SIZE_BYTES
    if (count < 0 || fromByteOffset < 0 || end > capacity()) {
        throw IndexOutOfBoundsException(
            "int range at byte offsets [$fromByteOffset, $end) is outside [0, ${capacity()})",
        )
    }
}
