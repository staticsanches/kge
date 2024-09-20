package dev.staticsanches.kge.resource

import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.nio.ByteBuffer

@JvmInline
internal value class OffHeapBuffer private constructor(
    val buffer: ByteBuffer,
) : KGECleanAction {
    constructor(size: Int) : this(MemoryUtil.memAlloc(size))

    override fun invoke() = MemoryUtil.memFree(buffer.clear())

    companion object {
        operator fun invoke(isProvider: () -> InputStream): OffHeapBuffer =
            isProvider().use { stream ->
                val bytes = stream.readAllBytes()
                OffHeapBuffer(bytes.size).applyAndInvokeIfFailed { it.buffer.put(bytes).clear() }
            }
    }
}
