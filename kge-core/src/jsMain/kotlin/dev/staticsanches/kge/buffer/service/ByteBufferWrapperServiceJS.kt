package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.ByteBufferWrapper
import dev.staticsanches.kge.buffer.ByteOrder
import dev.staticsanches.kge.buffer.isNative
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.ResourceWrapper.Companion.invoke
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.buffer.DataView

actual interface ByteBufferWrapperService : KGEExtensibleService {
    actual fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper

    fun create(
        arrayBuffer: ArrayBufferLike,
        name: String,
    ): ByteBufferWrapper

    actual fun duplicate(
        original: ByteBufferWrapper,
        newName: String?,
    ): ByteBufferWrapper

    actual companion object : ByteBufferWrapperService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalByteBufferWrapperServiceImplementation
}

actual val originalByteBufferWrapperServiceImplementation: ByteBufferWrapperService
    get() = DefaultByteBufferWrapperService

private data object DefaultByteBufferWrapperService : ByteBufferWrapperService {
    override fun create(
        capacity: Int,
        name: String,
    ): ByteBufferWrapper = create(ArrayBuffer(capacity), name)

    override fun duplicate(
        original: ByteBufferWrapper,
        newName: String?,
    ): ByteBufferWrapper =
        with(original.resource) {
            check(this is ArrayBufferWrapper) { "Not supported ByteBuffer" }
            return@with create(duplicate(), newName ?: original.toString())
        }

    override fun create(
        arrayBuffer: ArrayBufferLike,
        name: String,
    ): ByteBufferWrapper = ArrayBufferWrapper(arrayBuffer).let { wrapper -> ResourceWrapper(name, wrapper, wrapper) }

    override val servicePriority: Int
        get() = Int.MIN_VALUE

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

        fun duplicate(): ArrayBufferLike =
            (arrayBuffer ?: throw IllegalStateException("buffer is not available")).slice(0)

        override fun invoke() {
            arrayBuffer = null
            internalDataView = null
        }
    }
}
