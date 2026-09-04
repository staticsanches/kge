package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * Allocates [ByteBuffer]s in native memory.
 *
 * Allocation is an extension capability of the engine: the platform default
 * allocates off-heap memory (LWJGL on JVM, a `TypedArray` on the web) and a
 * consumer may replace it for the whole process via
 * [override][KGEOverridable.Proxy.override] — e.g. an alternate backend.
 */
interface MemoryAllocatorService : KGEOverridable {
    /**
     * Allocates a buffer of [sizeInBytes] bytes with unspecified content,
     * owned by the caller: close the returned wrapper to release the memory,
     * and never use its [ResourceWrapper.resource] after close.
     */
    fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer>

    companion object :
        KGEOverridable.Proxy<MemoryAllocatorService>(MemoryAllocatorService::class, memoryAllocatorDefault),
        MemoryAllocatorService {
        override fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer> {
            require(sizeInBytes >= 0) { "sizeInBytes must be >= 0: $sizeInBytes" }
            return delegate.allocate(sizeInBytes)
        }
    }
}

internal expect val memoryAllocatorDefault: MemoryAllocatorService
