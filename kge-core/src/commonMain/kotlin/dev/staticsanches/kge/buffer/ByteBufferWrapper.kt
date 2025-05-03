package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.ResourceWrapper

typealias ByteBufferWrapper = ResourceWrapper<ByteBuffer>

expect inline fun ByteBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String = { it },
): ByteBufferWrapper
