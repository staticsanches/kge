package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.ResourceWrapper.Companion.invoke
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import org.lwjgl.system.MemoryUtil

actual val originalByteBufferWrapperServiceImplementation: ByteBufferWrapperService
    get() = DefaultByteBufferWrapperService

private data object DefaultByteBufferWrapperService : ByteBufferWrapperService {
    override fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper =
        MemoryUtil.memAlloc(capacity).let { buffer ->
            ResourceWrapper(name, buffer, KGECleanAction { MemoryUtil.memFree(buffer) })
        }

    override fun duplicate(
        original: ByteBufferWrapper,
        newName: String?,
    ): ByteBufferWrapper =
        with(original.resource) {
            create(capacity(), newName ?: original.toString()).applyAndCloseIfFailed {
                MemoryUtil.memCopy(clear(), it.resource.clear())
            }
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
