@file:Suppress("unused")

package dev.staticsanches.kge.buffer

/**
 * Fills [length] consecutive integers (4 bytes each) with [value], starting at [byteIndex].
 *
 * The default implementation is per-pixel; platform actuals may override it with native bulk
 * operations (e.g. TypedArray.fill on JS) which are significantly faster on simple memory loops.
 */
expect fun ByteBuffer.fillInts(
    byteIndex: Int,
    length: Int,
    value: Int,
)

/**
 * Copies [length] consecutive integers (4 bytes each) from [source] at [sourceByteIndex] into
 * this buffer at [destByteIndex].
 *
 * The default implementation is per-pixel; platform actuals may override it with native bulk
 * operations (e.g. TypedArray.set on JS).
 */
expect fun ByteBuffer.copyInts(
    destByteIndex: Int,
    source: ByteBuffer,
    sourceByteIndex: Int,
    length: Int,
)
