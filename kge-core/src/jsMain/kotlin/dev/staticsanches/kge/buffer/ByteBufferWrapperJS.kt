@file:Suppress("unused")

package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.utils.toHumanReadableByteCountBin
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.buffer.DataView

actual inline fun ByteBufferWrapper(
    capacity: Int,
    nameFactory: (formattedCapacity: String) -> String,
): ByteBufferWrapper = ByteBufferWrapper(ArrayBuffer(capacity), nameFactory)

inline fun ByteBufferWrapper(
    arrayBuffer: ArrayBufferLike,
    nameFactory: (formattedCapacity: String) -> String = { it },
): ByteBufferWrapper = ByteBufferWrapper(arrayBuffer, nameFactory(arrayBuffer.byteLength.toHumanReadableByteCountBin()))

fun ByteBufferWrapper(
    arrayBuffer: ArrayBufferLike,
    name: String,
): ByteBufferWrapper =
    ArrayBufferWrapper(arrayBuffer).let { wrapper ->
        ResourceWrapper(name, wrapper, wrapper)
    }

private class ArrayBufferWrapper(
    arrayBuffer: ArrayBufferLike,
) : ByteBuffer(arrayBuffer.byteLength),
    KGECleanAction {
    private val littleEndian = ByteOrder.littleEndian.isNative

    private var arrayBuffer: ArrayBufferLike? = arrayBuffer
    private var internalDataView: DataView<*>? = DataView(arrayBuffer)

    override fun get(): Byte = internalDataView().getInt8(nextPosition(1))

    override fun get(position: Int): Byte = internalDataView().getInt8(checkPosition(position, 1))

    override fun put(value: Byte): ByteBuffer {
        internalDataView().setInt8(nextPosition(1), value)
        return this
    }

    override fun put(
        position: Int,
        value: Byte,
    ): ByteBuffer {
        internalDataView().setInt8(nextPosition(1), value)
        return this
    }

    override fun getInt(): Int = internalDataView().getInt32(nextPosition(4), littleEndian)

    override fun getInt(position: Int): Int = internalDataView().getInt32(checkPosition(position, 4), littleEndian)

    override fun putInt(value: Int): ByteBuffer {
        internalDataView().setInt32(nextPosition(4), value, littleEndian)
        return this
    }

    override fun putInt(
        position: Int,
        value: Int,
    ): ByteBuffer {
        internalDataView().setInt32(checkPosition(position, 4), value, littleEndian)
        return this
    }

    override fun getFloat(): Float = internalDataView().getFloat32(nextPosition(4), littleEndian)

    override fun getFloat(position: Int): Float =
        internalDataView().getFloat32(checkPosition(position, 4), littleEndian)

    override fun putFloat(value: Float): ByteBuffer {
        internalDataView().setFloat32(nextPosition(4), value, littleEndian)
        return this
    }

    override fun putFloat(
        position: Int,
        value: Float,
    ): ByteBuffer {
        internalDataView().setFloat32(checkPosition(position, 4), value, littleEndian)
        return this
    }

    private fun internalDataView(): DataView<*> =
        internalDataView ?: throw IllegalStateException("buffer is not available")

    override fun <V> createView(
        stride: Int,
        viewConstructor: (ArrayBufferLike, Int, Int) -> V,
    ): V =
        viewConstructor(
            arrayBuffer ?: throw IllegalStateException("buffer is not available"),
            position(), (limit() - position()) / stride,
        )

    override fun invoke() {
        arrayBuffer = null
        internalDataView = null
    }
}
