package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder

/** On JVM the engine buffer is the platform `java.nio.ByteBuffer` itself. */
actual typealias ByteBuffer = java.nio.ByteBuffer

/**
 * JVM backend: LWJGL off-heap memory — `memAlloc` (raw, unspecified content),
 * ordered little-endian, released with `memFree` at close.
 */
internal actual val memoryAllocatorDefault: MemoryAllocatorService = LwjglMemoryAllocator

private object LwjglMemoryAllocator : MemoryAllocatorService {
    override fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer> {
        val memory = MemoryUtil.memAlloc(sizeInBytes).order(ByteOrder.LITTLE_ENDIAN)
        return ResourceWrapper(
            "byte buffer ($sizeInBytes bytes)",
            memory,
            KGECleanAction { MemoryUtil.memFree(memory) },
        )
    }
}
