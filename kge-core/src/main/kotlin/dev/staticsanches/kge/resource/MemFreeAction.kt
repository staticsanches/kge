package dev.staticsanches.kge.resource

import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

internal class MemFreeAction(
    size: Int,
) : AutoCloseable,
    KGECleanAction {
    val buffer: ByteBuffer = MemoryUtil.memAlloc(size)

    override fun invoke() = close()

    override fun close() = MemoryUtil.memFree(buffer.clear())
}
