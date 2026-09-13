package dev.staticsanches.kge.buffer

import org.khronos.webgl.DataView
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Uint8Array

/**
 * Web [ByteBuffer] over a `TypedArray` (js + wasmJs). The `DataView` default
 * is big-endian, so int access passes the little-endian flag.
 */
actual abstract class ByteBuffer(
    sizeInBytes: Int,
) {
    private val bytes = Uint8Array(sizeInBytes)
    protected val view = DataView(bytes.buffer)

    /** The storage bytes, for platform consumers that need an `ArrayBufferView` (the GL backend). */
    internal val nativeBytes: Uint8Array get() = bytes

    /**
     * The storage as ints, or null when the size is not int-aligned. Used by
     * the native bulk fill/copy.
     */
    internal val nativeIntView: Int32Array? =
        if (sizeInBytes % Int.SIZE_BYTES == 0 && sizeInBytes > 0) {
            Int32Array(bytes.buffer)
        } else {
            null
        }

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
