package dev.staticsanches.kge.resource

import org.lwjgl.system.MemoryUtil
import java.nio.IntBuffer

@JvmInline
internal value class OffHeapIntBuffer private constructor(
    val buffer: IntBuffer,
) : KGECleanAction {
    constructor(size: Int) : this(MemoryUtil.memAllocInt(size))

    operator fun component1(): IntBuffer = buffer

    operator fun component2(): KGECleanAction = this

    override fun invoke() = MemoryUtil.memFree(buffer.clear())
}
