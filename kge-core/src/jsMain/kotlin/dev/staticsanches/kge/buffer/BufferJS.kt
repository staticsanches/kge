@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.utils.BytesSize.FLOAT
import dev.staticsanches.kge.utils.BytesSize.INT
import js.buffer.ArrayBufferLike
import js.buffer.DataView
import js.typedarrays.Float32Array
import js.typedarrays.Int32Array
import js.typedarrays.Uint8Array

actual abstract class Buffer(
    private val capacity: Int,
) {
    // Invariants: mark <= position <= limit <= capacity
    private var mark: Int = -1
    private var position: Int = 0
    private var limit: Int = capacity

    actual fun capacity(): Int = capacity

    actual fun position(): Int = position

    actual open fun position(newPosition: Int): Buffer {
        if (newPosition < 0 || newPosition > limit) {
            throw IllegalArgumentException("newPosition must lie in [0, $limit]")
        }
        position = newPosition
        if (mark > newPosition) mark = -1
        return this
    }

    actual fun limit(): Int = limit

    actual open fun limit(newLimit: Int): Buffer {
        if (newLimit < 0 || newLimit > capacity) {
            throw IllegalArgumentException("newLimit must lie in [0, $capacity]")
        }
        limit = newLimit
        if (position > newLimit) position = newLimit
        if (mark > newLimit) mark = -1
        return this
    }

    actual open fun mark(): Buffer {
        mark = position
        return this
    }

    actual open fun clear(): Buffer {
        mark = -1
        position = 0
        limit = capacity
        return this
    }

    actual fun remaining(): Int {
        val rem = limit - position
        return if (rem > 0) rem else 0
    }

    actual fun hasRemaining(): Boolean = position < limit

    actual open fun reset(): Buffer {
        val m = mark
        if (m < 0) {
            throw IllegalStateException()
        }
        position = m
        return this
    }

    actual open fun flip(): Buffer {
        limit = position
        position = 0
        mark = -1
        return this
    }

    fun asDataView(): DataView<ArrayBufferLike> = createView(1, ::DataView)

    fun asFloat32Array(): Float32Array<ArrayBufferLike> = createView(FLOAT, ::Float32Array)

    fun asInt32Array(): Int32Array<ArrayBufferLike> = createView(INT, ::Int32Array)

    fun asUint8Array(): Uint8Array<ArrayBufferLike> = createView(1, ::Uint8Array)

    protected abstract fun <V> createView(
        stride: Int,
        viewConstructor: (ArrayBufferLike, Int, Int) -> V,
    ): V
}
