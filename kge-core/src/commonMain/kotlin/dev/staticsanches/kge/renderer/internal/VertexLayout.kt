package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Pixel

/**
 * The built-in program's vertex layout: `pos4` (4 floats), `uv2` (2 floats) and
 * one packed little-endian RGBA tint (4 bytes) — the byte offsets the VAO is
 * bound with, plus the writer the draws use.
 */
internal object VertexLayout {
    const val POSITION_COMPONENTS = 4
    const val UV_COMPONENTS = 2
    const val COLOR_COMPONENTS = 4
    const val UV_OFFSET = POSITION_COMPONENTS * Float.SIZE_BYTES
    const val COLOR_OFFSET = UV_OFFSET + UV_COMPONENTS * Float.SIZE_BYTES

    /** Bytes of one vertex. */
    const val BYTES = COLOR_OFFSET + COLOR_COMPONENTS
}

/** Writes one vertex (floats as little-endian IEEE-754 bits) at [byteOffset]. */
internal fun ByteBuffer.putVertex(
    byteOffset: Int,
    x: Float,
    y: Float,
    z: Float,
    w: Float,
    u: Float,
    v: Float,
    tint: Pixel,
) {
    putInt(byteOffset, x.toRawBits())
    putInt(byteOffset + 4, y.toRawBits())
    putInt(byteOffset + 8, z.toRawBits())
    putInt(byteOffset + 12, w.toRawBits())
    putInt(byteOffset + 16, u.toRawBits())
    putInt(byteOffset + 20, v.toRawBits())
    putInt(byteOffset + 24, tint.nativeRGBA)
}
