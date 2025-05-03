@file:Suppress("unused")

package dev.staticsanches.kge.buffer

expect abstract class ByteBuffer {
    fun capacity(): Int

    fun position(): Int

    fun position(newPosition: Int): ByteBuffer

    fun limit(): Int

    fun limit(newLimit: Int): ByteBuffer

    fun mark(): ByteBuffer

    fun clear(): ByteBuffer

    fun hasRemaining(): Boolean

    fun order(): ByteOrder

    fun reset(): ByteBuffer

    fun flip(): ByteBuffer

    abstract fun get(): Byte

    abstract fun get(position: Int): Byte

    abstract fun put(value: Byte): ByteBuffer

    abstract fun put(
        position: Int,
        value: Byte,
    ): ByteBuffer

    abstract fun getInt(): Int

    abstract fun getInt(position: Int): Int

    abstract fun putInt(value: Int): ByteBuffer

    abstract fun putInt(
        position: Int,
        value: Int,
    ): ByteBuffer

    abstract fun getFloat(): Float

    abstract fun getFloat(position: Int): Float

    abstract fun putFloat(value: Float): ByteBuffer

    abstract fun putFloat(
        position: Int,
        value: Float,
    ): ByteBuffer
}

expect class ByteOrder

expect val ByteOrder.isNative: Boolean
