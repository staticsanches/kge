package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * Owns the [ByteBuffer] backends: allocation and the bulk copy/fill of whole
 * int regions.
 *
 * Allocation is process-wide overridable via
 * [override][KGEOverridable.Proxy.override]: the platform default allocates
 * off-heap memory (LWJGL on JVM, a `TypedArray` on the web).
 *
 * [fillInts]/[copyInts] default to a portable memmove-safe loop; the facade
 * validates the range before dispatch, so an override may call `super` as its
 * fallback.
 */
interface BufferService : KGEOverridable {
    /**
     * Allocates [sizeInBytes] bytes of unspecified content, owned by the
     * caller: close the returned wrapper to release the memory.
     *
     * [name] labels the allocation in leak and fail-fast messages.
     */
    fun allocate(
        sizeInBytes: Int,
        name: String? = null,
    ): ResourceWrapper<ByteBuffer>

    /**
     * Fills [count] ints of [target] with [value], the first at byte offset
     * [fromByteOffset].
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
     * Copies [count] ints from [source] to [dst], memmove-safe for overlapping
     * regions of one buffer. Byte offsets apply to [source] and [dst].
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
private fun ByteBuffer.requireRange(
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
