package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.Buffer
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
import dev.staticsanches.kge.buffer.wrapper.BufferWrapperType
import dev.staticsanches.kge.buffer.wrapper.ByteBufferWrapper
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin
import org.lwjgl.system.MemoryUtil
import java.io.File

actual interface BufferWrapperService : KGEExtensibleService {
    actual fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        capacity: Int,
        name: String,
    ): ResourceWrapper<B>

    actual fun <B : Buffer> duplicate(
        original: ResourceWrapper<B>,
        newName: String?,
    ): ResourceWrapper<B>

    fun readFile(name: String): ByteBufferWrapper

    actual companion object : BufferWrapperService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalBufferWrapperServiceImplementation
}

actual val originalBufferWrapperServiceImplementation: BufferWrapperService
    get() = DefaultBufferWrapperService

@Suppress("UNCHECKED_CAST")
private data object DefaultBufferWrapperService : BufferWrapperService {
    override fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        capacity: Int,
        name: String,
    ): ResourceWrapper<B> =
        with(
            when (type) {
                BufferWrapperType.Byte -> MemoryUtil.memAlloc(capacity)
                BufferWrapperType.Float -> MemoryUtil.memAllocFloat(capacity)
                BufferWrapperType.Int -> MemoryUtil.memAllocInt(capacity)
            } as B,
        ) {
            ResourceWrapper(name, this, KGECleanAction { MemoryUtil.memFree(clear()) })
        }

    override fun <B : Buffer> duplicate(
        original: ResourceWrapper<B>,
        newName: String?,
    ): ResourceWrapper<B> =
        with(original.resource) {
            when (this) {
                is ByteBuffer ->
                    create(BufferWrapperType.Byte, capacity(), newName ?: original.toString())
                        .applyClosingIfFailed { MemoryUtil.memCopy(clear(), resource.clear()) }

                is FloatBuffer ->
                    create(BufferWrapperType.Float, capacity(), newName ?: original.toString())
                        .applyClosingIfFailed { MemoryUtil.memCopy(clear(), resource.clear()) }

                is IntBuffer ->
                    create(BufferWrapperType.Int, capacity(), newName ?: original.toString())
                        .applyClosingIfFailed { MemoryUtil.memCopy(clear(), resource.clear()) }

                else -> throw IllegalArgumentException("Unsupported buffer wrapper")
            } as ResourceWrapper<B>
        }

    override fun readFile(name: String): ByteBufferWrapper =
        File(name).readBytes().let { bytes ->
            create(BufferWrapperType.Byte, bytes.size, "$name (${bytes.size.toHumanReadableByteCountBin()})")
                .applyClosingIfFailed {
                    resource.clear().put(bytes)
                }
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
