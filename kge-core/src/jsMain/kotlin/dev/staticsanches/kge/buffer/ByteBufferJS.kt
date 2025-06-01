@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.buffer

actual abstract class ByteBuffer(
    capacity: Int,
) : Buffer(capacity) {
    actual override fun position(newPosition: Int): ByteBuffer {
        super.position(newPosition)
        return this
    }

    actual override fun limit(newLimit: Int): ByteBuffer {
        super.limit(newLimit)
        return this
    }

    actual override fun mark(): ByteBuffer {
        super.mark()
        return this
    }

    actual override fun clear(): ByteBuffer {
        super.clear()
        return this
    }

    actual fun order(): ByteOrder = ByteOrder.nativeOrder

    actual override fun reset(): ByteBuffer {
        super.reset()
        return this
    }

    actual override fun flip(): ByteBuffer {
        super.flip()
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
}
