package dev.staticsanches.kge.resource

import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.nio.ByteBuffer

@JvmInline
internal value class OffHeapByteBuffer private constructor(
    val buffer: ByteBuffer,
) : KGECleanAction {
    constructor(size: Int) : this(MemoryUtil.memAlloc(size))

    operator fun component1(): ByteBuffer = buffer

    operator fun component2(): KGECleanAction = this

    override fun invoke() = MemoryUtil.memFree(buffer.clear())

    companion object {
        operator fun invoke(isProvider: () -> InputStream): OffHeapByteBuffer =
            isProvider().use { stream ->
                val bytes = stream.readAllBytes()
                OffHeapByteBuffer(bytes.size).applyAndInvokeIfFailed { it.buffer.put(bytes).clear() }
            }
    }
}
