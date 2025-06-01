@file:Suppress("unused")

package dev.staticsanches.kge.buffer.wrapper

import dev.staticsanches.kge.buffer.Buffer
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
import dev.staticsanches.kge.buffer.service.BufferWrapperService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.utils.BytesSize.FLOAT
import dev.staticsanches.kge.utils.BytesSize.INT
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin

sealed interface BufferWrapperType<B : Buffer> {
    data object Byte : BufferWrapperType<ByteBuffer>

    data object Float : BufferWrapperType<FloatBuffer>

    data object Int : BufferWrapperType<IntBuffer>
}

typealias ByteBufferWrapper = ResourceWrapper<ByteBuffer>

inline fun ByteBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String = { it },
): ByteBufferWrapper =
    BufferWrapperService.create(
        BufferWrapperType.Byte, capacity, nameFactory(capacity.toHumanReadableByteCountBin()),
    )

typealias FloatBufferWrapper = ResourceWrapper<FloatBuffer>

inline fun FloatBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String = { it },
): FloatBufferWrapper =
    BufferWrapperService.create(
        BufferWrapperType.Float, capacity, nameFactory((capacity * FLOAT).toHumanReadableByteCountBin()),
    )

typealias IntBufferWrapper = ResourceWrapper<IntBuffer>

inline fun IntBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String = { it },
): IntBufferWrapper =
    BufferWrapperService.create(
        BufferWrapperType.Int, capacity, nameFactory((capacity * INT).toHumanReadableByteCountBin()),
    )
