package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import org.lwjgl.system.MemoryUtil
import java.nio.ByteOrder

/** On JVM the engine buffer is the platform `java.nio.ByteBuffer` itself. */
actual typealias ByteBuffer = java.nio.ByteBuffer

/** JVM backend: LWJGL off-heap memory, little-endian, freed at close. */
internal actual val bufferServiceDefault: BufferService = LwjglBufferService

private object LwjglBufferService : BufferService {
    override fun allocate(
        sizeInBytes: Int,
        name: String?,
    ): ResourceWrapper<ByteBuffer> {
        val memory = MemoryUtil.memAlloc(sizeInBytes).order(ByteOrder.LITTLE_ENDIAN)
        val label = name?.let { "${formatBytes(sizeInBytes)} ($it)" } ?: formatBytes(sizeInBytes)
        return ResourceWrapper(
            "byte buffer ($label)",
            memory,
            KGECleanAction { MemoryUtil.memFree(memory) },
        )
    }

    override fun copyInts(
        dst: ByteBuffer,
        dstFromByteOffset: Int,
        source: ByteBuffer,
        sourceFromByteOffset: Int,
        count: Int,
    ) {
        if (
            source === dst ||
            dst.isReadOnly ||
            dstFromByteOffset % Int.SIZE_BYTES != 0 ||
            sourceFromByteOffset % Int.SIZE_BYTES != 0
        ) {
            return super.copyInts(dst, dstFromByteOffset, source, sourceFromByteOffset, count)
        }

        val bytes = count.toLong() * Int.SIZE_BYTES
        val dstView = dst.duplicate()
        val srcView = source.duplicate()
        dstView.position(dstFromByteOffset)
        dstView.limit((dstFromByteOffset + bytes).toInt())
        srcView.position(sourceFromByteOffset)
        srcView.limit((sourceFromByteOffset + bytes).toInt())
        dstView.asIntBuffer().put(srcView.asIntBuffer())
    }
}
