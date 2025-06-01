package dev.staticsanches.kge.buffer

expect abstract class IntBuffer : Buffer {
    final override fun position(newPosition: Int): IntBuffer

    final override fun limit(newLimit: Int): IntBuffer

    final override fun mark(): IntBuffer

    final override fun clear(): IntBuffer

    abstract fun order(): ByteOrder

    final override fun reset(): IntBuffer

    final override fun flip(): IntBuffer

    abstract fun get(): Int

    abstract fun get(position: Int): Int

    abstract fun put(value: Int): IntBuffer

    abstract fun put(
        position: Int,
        value: Int,
    ): IntBuffer
}
