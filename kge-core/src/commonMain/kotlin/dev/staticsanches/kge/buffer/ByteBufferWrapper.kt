package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.buffer.service.ByteBufferWrapperService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin

typealias ByteBufferWrapper = ResourceWrapper<ByteBuffer>

inline fun ByteBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String = { it },
): ByteBufferWrapper = ByteBufferWrapperService.create(capacity, nameFactory(capacity.toHumanReadableByteCountBin()))
