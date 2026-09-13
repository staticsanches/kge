package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper

/** Web backend (js + wasmJs): `TypedArray` memory, GC-released, so close is a no-op. */
internal actual val bufferServiceDefault: BufferService = WebBufferService

private object WebBufferService : BufferService {
    override fun allocate(
        sizeInBytes: Int,
        name: String?,
    ): ResourceWrapper<ByteBuffer> =
        ResourceWrapper(
            "byte buffer (${name?.let { "${formatBytes(sizeInBytes)} ($it)" } ?: formatBytes(sizeInBytes)})",
            WebByteBuffer(sizeInBytes),
            KGECleanAction { },
        )

    override fun fillInts(
        target: ByteBuffer,
        fromByteOffset: Int,
        count: Int,
        value: Int,
    ) {
        val ints = target.nativeIntView
        if (ints == null || fromByteOffset % Int.SIZE_BYTES != 0) {
            return super.fillInts(target, fromByteOffset, count, value)
        }

        val start = fromByteOffset / Int.SIZE_BYTES
        target.putInt(fromByteOffset, value)
        var filled = 1
        while (filled < count) {
            val block = minOf(filled, count - filled)
            ints.set(ints.subarray(start, start + block), start + filled)
            filled += block
        }
    }

    override fun copyInts(
        dst: ByteBuffer,
        dstFromByteOffset: Int,
        source: ByteBuffer,
        sourceFromByteOffset: Int,
        count: Int,
    ) {
        val dstInts = dst.nativeIntView
        val srcInts = source.nativeIntView
        if (
            dstInts == null ||
            srcInts == null ||
            dstFromByteOffset % Int.SIZE_BYTES != 0 ||
            sourceFromByteOffset % Int.SIZE_BYTES != 0
        ) {
            return super.copyInts(dst, dstFromByteOffset, source, sourceFromByteOffset, count)
        }

        val dstStart = dstFromByteOffset / Int.SIZE_BYTES
        val srcStart = sourceFromByteOffset / Int.SIZE_BYTES
        dstInts.set(srcInts.subarray(srcStart, srcStart + count), dstStart)
    }
}

private class WebByteBuffer(
    sizeInBytes: Int,
) : ByteBuffer(sizeInBytes) {
    override fun get(index: Int): Byte {
        checkIndex(index, sizeInBytes = 1)
        return view.getUint8(index)
    }

    override fun put(
        index: Int,
        value: Byte,
    ): ByteBuffer {
        checkIndex(index, sizeInBytes = 1)
        view.setUint8(index, value)
        return this
    }

    override fun getInt(index: Int): Int {
        checkIndex(index, sizeInBytes = Int.SIZE_BYTES)
        return view.getInt32(index, true)
    }

    override fun putInt(
        index: Int,
        value: Int,
    ): ByteBuffer {
        checkIndex(index, sizeInBytes = Int.SIZE_BYTES)
        view.setInt32(index, value, true)
        return this
    }

    /** Throws [IndexOutOfBoundsException] unless the whole data unit fits at [index]. */
    private fun checkIndex(
        index: Int,
        sizeInBytes: Int,
    ) {
        val lastValidStart = capacity() - sizeInBytes
        if (index < 0 || index > lastValidStart) {
            throw IndexOutOfBoundsException("index $index is outside [0, $lastValidStart]")
        }
    }
}
