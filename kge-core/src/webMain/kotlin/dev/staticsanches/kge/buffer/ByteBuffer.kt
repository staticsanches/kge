package dev.staticsanches.kge.buffer

import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array

/**
 * Web buffer: an emulation of [ByteBuffer] over a `TypedArray`, shared by the
 * js and wasmJs targets.
 *
 * The DataView default is big-endian, so every int access passes the
 * little-endian flag.
 */
actual abstract class ByteBuffer(
    sizeInBytes: Int,
) {
    private val bytes = Uint8Array(sizeInBytes)
    protected val view = DataView(bytes.buffer)

    actual fun capacity(): Int = bytes.length

    actual abstract fun get(index: Int): Byte

    actual abstract fun put(
        index: Int,
        value: Byte,
    ): ByteBuffer

    actual abstract fun getInt(index: Int): Int

    actual abstract fun putInt(
        index: Int,
        value: Int,
    ): ByteBuffer
}
