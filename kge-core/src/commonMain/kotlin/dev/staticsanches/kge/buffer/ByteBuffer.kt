package dev.staticsanches.kge.buffer

expect abstract class ByteBuffer : Buffer {
    override fun position(newPosition: Int): ByteBuffer

    override fun limit(newLimit: Int): ByteBuffer

    override fun mark(): ByteBuffer

    override fun clear(): ByteBuffer

    fun order(): ByteOrder

    override fun reset(): ByteBuffer

    override fun flip(): ByteBuffer

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
