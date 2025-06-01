@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.buffer

actual abstract class FloatBuffer(
    capacity: Int,
) : Buffer(capacity) {
    actual final override fun position(newPosition: Int): FloatBuffer {
        super.position(newPosition)
        return this
    }

    actual final override fun limit(newLimit: Int): FloatBuffer {
        super.limit(newLimit)
        return this
    }

    actual final override fun mark(): FloatBuffer {
        super.mark()
        return this
    }

    actual final override fun clear(): FloatBuffer {
        super.clear()
        return this
    }

    actual abstract fun order(): ByteOrder

    actual final override fun reset(): FloatBuffer {
        super.reset()
        return this
    }

    actual final override fun flip(): FloatBuffer {
        super.flip()
        return this
    }

    actual abstract fun get(): Float

    actual abstract fun get(position: Int): Float

    actual abstract fun put(value: Float): FloatBuffer

    actual abstract fun put(
        position: Int,
        value: Float,
    ): FloatBuffer
}
