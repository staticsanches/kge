package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.math.vector.Int2D

/**
 * An open native face over a payload it does not own: closing releases native
 * handles only, so the payload must outlive the face. Built by the seam's factory.
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

/** Applied axis coordinates: tags and raw 16.16 values in `fvar` order. */
internal class AxisCoordinates(
    val tags: IntArray,
    val values: IntArray,
) {
    val isEmpty: Boolean get() = tags.isEmpty()

    companion object {
        /** The default instance: no engine call at all. */
        val Empty: AxisCoordinates = AxisCoordinates(IntArray(0), IntArray(0))
    }
}

/**
 * Opens [payload] as its first face and applies [coordinates] to both engines
 * when they are non-empty; a payload that is not a usable font is rejected.
 */
internal expect suspend fun createNativeFace(
    payload: TtfPayload,
    coordinates: AxisCoordinates,
): NativeFace

/** Releases the native handles, never the shared payload; the wrapper runs it at most once. */
internal expect fun closeNativeFace(face: NativeFace)
