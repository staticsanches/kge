package dev.staticsanches.kge.buffer

expect abstract class FloatBuffer : Buffer {
    final override fun position(newPosition: Int): FloatBuffer

    final override fun limit(newLimit: Int): FloatBuffer

    final override fun mark(): FloatBuffer

    final override fun clear(): FloatBuffer

    abstract fun order(): ByteOrder

    final override fun reset(): FloatBuffer

    final override fun flip(): FloatBuffer

    abstract fun get(): Float

    abstract fun get(position: Int): Float

    abstract fun put(value: Float): FloatBuffer

    abstract fun put(
        position: Int,
        value: Float,
    ): FloatBuffer
}
