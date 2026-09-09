package dev.staticsanches.kge.buffer

import kotlin.math.roundToInt

/**
 * Byte-addressable storage in native memory — a `java.nio.ByteBuffer` on JVM,
 * a `TypedArray` view on the web targets.
 *
 * All indices are byte offsets. Ints are little-endian. The content of a
 * freshly allocated buffer is unspecified — set every byte before reading it.
 * Access outside the buffer throws [IndexOutOfBoundsException].
 */
expect abstract class ByteBuffer {
    /** The buffer size in bytes. */
    fun capacity(): Int

    /** Reads the byte at [index] (signed). */
    abstract fun get(index: Int): Byte

    /** Writes [value] at [index] and returns this buffer. */
    abstract fun put(
        index: Int,
        value: Byte,
    ): ByteBuffer

    /** Reads the int at [index]. */
    abstract fun getInt(index: Int): Int

    /** Writes [value] at [index] and returns this buffer. */
    abstract fun putInt(
        index: Int,
        value: Int,
    ): ByteBuffer
}

/** Reads the byte at [byteOffset] as an unsigned value (0..255). */
fun ByteBuffer.byteAt(byteOffset: Int): Int = get(byteOffset).toInt() and 0xFF

/**
 * Writes [value] (0..255) at [byteOffset]. Values outside `0..255` throw
 * [IllegalArgumentException].
 */
fun ByteBuffer.putByte(
    byteOffset: Int,
    value: Int,
) {
    require(value in 0..255) { "value must be in 0..255: $value" }
    put(byteOffset, value.toByte())
}

/**
 * Formats [sizeInBytes] human-readably, 1024-based: `B`, `KB`, `MB` or `GB`,
 * one decimal while below 10 and an integer at 10 and up.
 */
fun formatBytes(sizeInBytes: Int): String {
    var value = sizeInBytes.toFloat()
    val units = arrayOf("B", "KB", "MB", "GB")
    var unit = 0
    while (value >= 1024f && unit < units.lastIndex) {
        value /= 1024f
        unit++
    }
    val text =
        if (value >= 10f) {
            value.roundToInt().toString()
        } else {
            val tenths = (value * 10).roundToInt()
            "${tenths / 10}.${tenths % 10}"
        }
    return "$text ${units[unit]}"
}

/**
 * Fills [count] consecutive int slots (4 bytes each) with [value], the first
 * at byte offset [fromByteOffset]. A [count] of zero is a no-op. The region
 * is validated and filled by the [BufferService] (whose default is the
 * portable loop and whose platform defaults add their native paths).
 */
fun ByteBuffer.fillInts(
    fromByteOffset: Int,
    count: Int,
    value: Int,
) {
    BufferService.fillInts(this, fromByteOffset, count, value)
}

/**
 * Copies [count] ints from [source] at byte offset [sourceFromByteOffset] into
 * this buffer at byte offset [dstFromByteOffset]. Overlapping copies within
 * one buffer are memmove-safe. A [count] of zero is a no-op. The region is
 * validated and copied by the [BufferService] (whose default is the portable
 * memmove loop and whose platform defaults add their native paths).
 */
fun ByteBuffer.copyInts(
    dstFromByteOffset: Int,
    source: ByteBuffer,
    sourceFromByteOffset: Int,
    count: Int,
) {
    BufferService.copyInts(this, dstFromByteOffset, source, sourceFromByteOffset, count)
}
