package dev.staticsanches.kge.buffer.service

import dev.staticsanches.kge.buffer.Buffer
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.ByteOrder
import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
import dev.staticsanches.kge.buffer.isNative
import dev.staticsanches.kge.buffer.wrapper.BufferWrapperType
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.utils.BytesSize.FLOAT
import dev.staticsanches.kge.utils.BytesSize.INT
import js.buffer.ArrayBuffer
import js.buffer.ArrayBufferLike
import js.buffer.DataView

actual interface BufferWrapperService : KGEExtensibleService {
    actual fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        capacity: Int,
        name: String,
    ): ResourceWrapper<B>

    fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        data: ArrayBufferLike,
        name: String,
    ): ResourceWrapper<B>

    actual fun <B : Buffer> duplicate(
        original: ResourceWrapper<B>,
        newName: String?,
    ): ResourceWrapper<B>

    actual companion object : BufferWrapperService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalBufferWrapperServiceImplementation
}

actual val originalBufferWrapperServiceImplementation: BufferWrapperService
    get() = DefaultBufferWrapperService

@Suppress("UNCHECKED_CAST")
private data object DefaultBufferWrapperService : BufferWrapperService {
    override fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        capacity: Int,
        name: String,
    ): ResourceWrapper<B> =
        when (type) {
            BufferWrapperType.Byte -> ArrayBuffer(capacity)
            BufferWrapperType.Float -> ArrayBuffer(capacity * FLOAT)
            BufferWrapperType.Int -> ArrayBuffer(capacity * INT)
        }.let { create(type, it, name) }

    override fun <B : Buffer> create(
        type: BufferWrapperType<B>,
        data: ArrayBufferLike,
        name: String,
    ): ResourceWrapper<B> =
        with(
            when (type) {
                BufferWrapperType.Byte -> ArrayBufferAsByteBuffer(data)
                BufferWrapperType.Float -> ArrayBufferAsFloatBuffer(data)
                BufferWrapperType.Int -> ArrayBufferAsIntBuffer(data)
            } as B,
        ) { ResourceWrapper(name, this, this as KGECleanAction) }

    override fun <B : Buffer> duplicate(
        original: ResourceWrapper<B>,
        newName: String?,
    ): ResourceWrapper<B> =
        with(original.resource) {
            when (this) {
                is ArrayBufferAsByteBuffer ->
                    create(BufferWrapperType.Byte, duplicate(), newName ?: original.toString())

                is ArrayBufferAsFloatBuffer ->
                    create(BufferWrapperType.Float, duplicate(), newName ?: original.toString())

                is ArrayBufferAsIntBuffer ->
                    create(BufferWrapperType.Int, duplicate(), newName ?: original.toString())

                else -> throw IllegalArgumentException("Unsupported buffer wrapper")
            } as ResourceWrapper<B>
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

private class ArrayBufferAsByteBuffer(
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

    override fun getInt(position: Int): Int = internalDataView().getInt32(checkPosition(position, INT), littleEndian)

    override fun putInt(value: Int): ByteBuffer {
        internalDataView().setInt32(nextPosition(INT), value, littleEndian)
        return this
    }

    override fun putInt(
        position: Int,
        value: Int,
    ): ByteBuffer {
        internalDataView().setInt32(checkPosition(position, INT), value, littleEndian)
        return this
    }

    override fun getFloat(): Float = internalDataView().getFloat32(nextPosition(FLOAT), littleEndian)

    override fun getFloat(position: Int): Float =
        internalDataView().getFloat32(checkPosition(position, FLOAT), littleEndian)

    override fun putFloat(value: Float): ByteBuffer {
        internalDataView().setFloat32(nextPosition(FLOAT), value, littleEndian)
        return this
    }

    override fun putFloat(
        position: Int,
        value: Float,
    ): ByteBuffer {
        internalDataView().setFloat32(checkPosition(position, FLOAT), value, littleEndian)
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

    fun duplicate(): ArrayBufferLike = (arrayBuffer ?: throw IllegalStateException("buffer is not available")).slice(0)

    override fun invoke() {
        arrayBuffer = null
        internalDataView = null
    }

    private fun nextPosition(numberOfBytes: Int): Int {
        val p = position()
        super.position(p + numberOfBytes)
        return p
    }

    private fun checkPosition(
        position: Int,
        numberOfBytes: Int,
    ): Int {
        if (position < 0 || numberOfBytes > limit() - position) throw IndexOutOfBoundsException()
        return position
    }
}

private class ArrayBufferAsFloatBuffer(
    arrayBuffer: ArrayBufferLike,
) : FloatBuffer(arrayBuffer.byteLength / FLOAT),
    KGECleanAction {
    init {
        check(arrayBuffer.byteLength % FLOAT == 0) { "byteLength must be multiple of $FLOAT" }
    }

    private val littleEndian = ByteOrder.littleEndian.isNative

    private var arrayBuffer: ArrayBufferLike? = arrayBuffer
    private var internalDataView: DataView<*>? = DataView(arrayBuffer)

    override fun order(): ByteOrder = ByteOrder.nativeOrder

    override fun get(): Float = internalDataView().getFloat32(nextPosition(), littleEndian)

    override fun get(position: Int): Float = internalDataView().getFloat32(checkPosition(position), littleEndian)

    override fun put(value: Float): FloatBuffer {
        internalDataView().setFloat32(nextPosition(), value, littleEndian)
        return this
    }

    override fun put(
        position: Int,
        value: Float,
    ): FloatBuffer {
        internalDataView().setFloat32(checkPosition(position), value, littleEndian)
        return this
    }

    override fun <V> createView(
        stride: Int,
        viewConstructor: (ArrayBufferLike, Int, Int) -> V,
    ): V =
        viewConstructor(
            arrayBuffer ?: throw IllegalStateException("buffer is not available"),
            position() * FLOAT, (limit() - position()) * FLOAT / stride,
        )

    fun duplicate(): ArrayBufferLike = (arrayBuffer ?: throw IllegalStateException("buffer is not available")).slice(0)

    private fun internalDataView(): DataView<*> =
        internalDataView ?: throw IllegalStateException("buffer is not available")

    override fun invoke() {
        arrayBuffer = null
        internalDataView = null
    }

    private fun nextPosition(): Int {
        val p = position()
        position(p + 1)
        return p * FLOAT
    }

    private fun checkPosition(position: Int): Int {
        if (position < 0 || position >= limit()) throw IndexOutOfBoundsException()
        return position * FLOAT
    }
}

private class ArrayBufferAsIntBuffer(
    arrayBuffer: ArrayBufferLike,
) : IntBuffer(arrayBuffer.byteLength / INT),
    KGECleanAction {
    init {
        check(arrayBuffer.byteLength % INT == 0) { "byteLength must be multiple of $INT" }
    }

    private val littleEndian = ByteOrder.littleEndian.isNative

    private var arrayBuffer: ArrayBufferLike? = arrayBuffer
    private var internalDataView: DataView<*>? = DataView(arrayBuffer)

    override fun order(): ByteOrder = ByteOrder.nativeOrder

    override fun get(): Int = internalDataView().getInt32(nextPosition(), littleEndian)

    override fun get(position: Int): Int = internalDataView().getInt32(checkPosition(position), littleEndian)

    override fun put(value: Int): IntBuffer {
        internalDataView().setInt32(nextPosition(), value, littleEndian)
        return this
    }

    override fun put(
        position: Int,
        value: Int,
    ): IntBuffer {
        internalDataView().setInt32(checkPosition(position), value, littleEndian)
        return this
    }

    override fun <V> createView(
        stride: Int,
        viewConstructor: (ArrayBufferLike, Int, Int) -> V,
    ): V =
        viewConstructor(
            arrayBuffer ?: throw IllegalStateException("buffer is not available"),
            position() * INT, (limit() - position()) * INT / stride,
        )

    fun duplicate(): ArrayBufferLike = (arrayBuffer ?: throw IllegalStateException("buffer is not available")).slice(0)

    private fun internalDataView(): DataView<*> =
        internalDataView ?: throw IllegalStateException("buffer is not available")

    override fun invoke() {
        arrayBuffer = null
        internalDataView = null
    }

    private fun nextPosition(): Int {
        val p = position()
        position(p + 1)
        return p * INT
    }

    private fun checkPosition(position: Int): Int {
        if (position < 0 || position >= limit()) throw IndexOutOfBoundsException()
        return position * INT
    }
}
