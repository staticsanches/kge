@file:Suppress("unused")

package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import js.buffer.ArrayBufferLike

actual abstract class ByteBuffer internal constructor(
    private val capacity: Int,
) {
    // Invariants: mark <= position <= limit <= capacity
    private var mark: Int = -1
    private var position: Int = 0
    private var limit: Int = capacity

    @KGESensitiveAPI
    abstract fun <V> createView(
        stride: Int,
        viewConstructor: (ArrayBufferLike, Int, Int) -> V,
    ): V

    actual fun capacity(): Int = capacity

    actual fun position(): Int = position

    actual fun position(newPosition: Int): ByteBuffer {
        if (newPosition < 0 || newPosition > limit) {
            throw IllegalArgumentException("newPosition must lie in [0, $limit]")
        }
        position = newPosition
        if (mark > newPosition) mark = -1
        return this
    }

    actual fun limit(): Int = limit

    actual fun limit(newLimit: Int): ByteBuffer {
        if (newLimit < 0 || newLimit > capacity) {
            throw IllegalArgumentException("newLimit must lie in [0, $capacity]")
        }
        limit = newLimit
        if (position > newLimit) position = newLimit
        if (mark > newLimit) mark = -1
        return this
    }

    actual fun mark(): ByteBuffer {
        mark = position
        return this
    }

    actual fun clear(): ByteBuffer {
        mark = -1
        position = 0
        limit = capacity
        return this
    }

    actual fun hasRemaining(): Boolean = position < limit

    actual fun order(): ByteOrder = ByteOrder.nativeOrder

    actual fun reset(): ByteBuffer {
        val m = mark
        if (m < 0) {
            throw IllegalStateException()
        }
        position = m
        return this
    }

    actual fun flip(): ByteBuffer {
        limit = position
        position = 0
        mark = -1
        return this
    }

    actual abstract fun get(): Byte

    actual abstract fun get(position: Int): Byte

    actual abstract fun put(value: Byte): ByteBuffer

    actual abstract fun put(
        position: Int,
        value: Byte,
    ): ByteBuffer

    actual abstract fun getInt(): Int

    actual abstract fun getInt(position: Int): Int

    actual abstract fun putInt(value: Int): ByteBuffer

    actual abstract fun putInt(
        position: Int,
        value: Int,
    ): ByteBuffer

    actual abstract fun getFloat(): Float

    actual abstract fun getFloat(position: Int): Float

    actual abstract fun putFloat(value: Float): ByteBuffer

    actual abstract fun putFloat(
        position: Int,
        value: Float,
    ): ByteBuffer

    protected fun nextPosition(numberOfBytes: Int): Int {
        val p = position
        if (limit - p < numberOfBytes) {
            throw IndexOutOfBoundsException()
        }
        position = p + numberOfBytes
        return p
    }

    protected fun checkPosition(
        position: Int,
        numberOfBytes: Int,
    ): Int {
        if (position < 0 || numberOfBytes > limit - position) throw IndexOutOfBoundsException()
        return position
    }
}

actual class ByteOrder private constructor(
    private val name: String,
) {
    override fun toString(): String = name

    companion object {
        val bigEndian: ByteOrder = ByteOrder("BIG_ENDIAN")
        val littleEndian: ByteOrder = ByteOrder("LITTLE_ENDIAN")
        val nativeOrder =
            if (js("new Uint8Array(new Uint16Array([1]).buffer)[0] === 0")) bigEndian else littleEndian
    }
}

actual val ByteOrder.isNative: Boolean
    get() = this === ByteOrder.nativeOrder
