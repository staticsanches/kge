@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.buffer

actual abstract class IntBuffer(
    capacity: Int,
) : Buffer(capacity) {
    actual final override fun position(newPosition: Int): IntBuffer {
        super.position(newPosition)
        return this
    }

    actual final override fun limit(newLimit: Int): IntBuffer {
        super.limit(newLimit)
        return this
    }

    actual final override fun mark(): IntBuffer {
        super.mark()
        return this
    }

    actual final override fun clear(): IntBuffer {
        super.clear()
        return this
    }

    actual abstract fun order(): ByteOrder

    actual final override fun reset(): IntBuffer {
        super.reset()
        return this
    }

    actual final override fun flip(): IntBuffer {
        super.flip()
        return this
    }

    actual abstract fun get(): Int

    actual abstract fun get(position: Int): Int

    actual abstract fun put(value: Int): IntBuffer

    actual abstract fun put(
        position: Int,
        value: Int,
    ): IntBuffer
}
