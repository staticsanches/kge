package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.ResourceWrapper.Companion.invoke
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import org.lwjgl.system.MemoryUtil

actual interface ByteBufferWrapperService : KGEExtensibleService {
    actual fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper

    actual fun duplicate(
        original: ByteBufferWrapper,
        newName: String?,
    ): ByteBufferWrapper

    actual companion object : ByteBufferWrapperService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalByteBufferWrapperServiceImplementation
}

actual val originalByteBufferWrapperServiceImplementation: ByteBufferWrapperService
    get() = DefaultByteBufferWrapperService

private data object DefaultByteBufferWrapperService : ByteBufferWrapperService {
    override fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper =
        MemoryUtil.memAlloc(capacity).let { buffer ->
            ResourceWrapper(name, buffer, KGECleanAction { MemoryUtil.memFree(buffer.clear()) })
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
