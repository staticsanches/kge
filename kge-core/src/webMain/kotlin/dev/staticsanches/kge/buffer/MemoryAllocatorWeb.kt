package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * Web backend (js + wasmJs): memory over a `TypedArray`. Web memory is
 * garbage-collected, so the release action is a no-op — the wrapper contract
 * owns the lifetime and the leak detection.
 */
internal actual val memoryAllocatorDefault: MemoryAllocatorService = WebMemoryAllocator

private object WebMemoryAllocator : MemoryAllocatorService {
    override fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer> =
        ResourceWrapper(
            "byte buffer ($sizeInBytes bytes)",
            WebByteBuffer(sizeInBytes),
            KGECleanAction { },
        )
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
