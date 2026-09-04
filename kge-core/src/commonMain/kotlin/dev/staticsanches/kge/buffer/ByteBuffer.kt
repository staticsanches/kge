package dev.staticsanches.kge.buffer

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
 * Fills [count] consecutive int slots (4 bytes each) with [value], the first
 * at byte offset [fromByteOffset]. A [count] of zero is a no-op.
 */
fun ByteBuffer.fillInts(
    fromByteOffset: Int,
    count: Int,
    value: Int,
) {
    requireRange(fromByteOffset, count)
    for (i in 0 until count) {
        putInt(fromByteOffset + i * Int.SIZE_BYTES, value)
    }
}

/**
 * Copies [count] ints from [source] at byte offset [sourceFromByteOffset] into
 * this buffer at byte offset [dstFromByteOffset]. Overlapping copies within
 * one buffer are memmove-safe. A [count] of zero is a no-op.
 */
fun ByteBuffer.copyInts(
    dstFromByteOffset: Int,
    source: ByteBuffer,
    sourceFromByteOffset: Int,
    count: Int,
) {
    requireRange(dstFromByteOffset, count)
    source.requireRange(sourceFromByteOffset, count)
    // memmove: when the destination starts past the source, iterate backward
    // so an int is read before a write could overwrite it.
    if (source === this && dstFromByteOffset > sourceFromByteOffset) {
        for (i in count - 1 downTo 0) {
            putInt(
                dstFromByteOffset + i * Int.SIZE_BYTES,
                source.getInt(sourceFromByteOffset + i * Int.SIZE_BYTES),
            )
        }
    } else {
        for (i in 0 until count) {
            putInt(
                dstFromByteOffset + i * Int.SIZE_BYTES,
                source.getInt(sourceFromByteOffset + i * Int.SIZE_BYTES),
            )
        }
    }
}

/** Throws [IndexOutOfBoundsException] unless [count] ints fit at [fromByteOffset]. */
private fun ByteBuffer.requireRange(
    fromByteOffset: Int,
    count: Int,
) {
    val end = fromByteOffset.toLong() + count.toLong() * Int.SIZE_BYTES
    if (count < 0 || fromByteOffset < 0 || end > capacity()) {
        throw IndexOutOfBoundsException(
            "int range at byte offsets [$fromByteOffset, $end) is outside [0, ${capacity()})",
        )
    }
}
