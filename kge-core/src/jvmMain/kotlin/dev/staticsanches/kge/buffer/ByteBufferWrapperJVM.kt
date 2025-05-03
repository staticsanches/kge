@file:Suppress("unused")

package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin
import org.lwjgl.system.MemoryUtil

actual inline fun ByteBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String,
): ByteBufferWrapper =
    nameFactory(capacity.toHumanReadableByteCountBin()).let { name ->
        MemoryUtil.memAlloc(capacity).let { buffer ->
            ResourceWrapper(
                name,
                buffer,
                KGECleanAction { MemoryUtil.memFree(buffer) },
            )
        }
    }
