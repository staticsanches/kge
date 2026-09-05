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
     *
     * [name] identifies the allocation in the wrapper's leak/fail-fast
     * messages — a diagnostic label, nullable for anonymous buffers.
     */
    fun allocate(
        sizeInBytes: Int,
        name: String? = null,
    ): ResourceWrapper<ByteBuffer>

    companion object :
        KGEOverridable.Proxy<MemoryAllocatorService>(MemoryAllocatorService::class, memoryAllocatorDefault),
        MemoryAllocatorService {
        override fun allocate(
            sizeInBytes: Int,
            name: String?,
        ): ResourceWrapper<ByteBuffer> {
            require(sizeInBytes >= 0) { "sizeInBytes must be >= 0: $sizeInBytes" }
            return delegate.allocate(sizeInBytes, name)
        }
    }
}

internal expect val memoryAllocatorDefault: MemoryAllocatorService
