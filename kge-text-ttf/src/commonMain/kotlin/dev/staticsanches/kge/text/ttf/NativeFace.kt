package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * An open native face standing on an engine-owned font payload, released no
 * later than the face's close. Built by the seam's factory, never by callers.
 */
internal expect class NativeFace {
    fun shape(
        codePoints: IntArray,
        sizePx: Int,
    ): List<ShapedGlyph>

    fun metrics(sizePx: Int): TextMetrics

    /** Renders [glyphId] at [sizePx]; a glyph with no ink yields no coverage. */
    fun rasterize(
        glyphId: Int,
        sizePx: Int,
    ): GlyphCoverage
}

/** One rendered glyph: row-major, top-down, one coverage byte per pixel. */
internal class GlyphCoverage(
    val width: Int,
    val height: Int,
    /** Pen to bitmap top-left, in whole pixels, y down. */
    val bearing: Int2D,
    /** `width * height` bytes; empty when the glyph has no ink. */
    val coverage: ByteArray,
)

/**
 * Opens [bytes] as its first face and validates it; a payload that is not a
 * usable font is rejected and the face does not own the payload then.
 */
internal expect suspend fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace

/** Releases the native handles; the wrapper runs it at most once. */
internal expect fun closeNativeFace(face: NativeFace)
