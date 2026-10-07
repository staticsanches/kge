package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.math.vector.Float2D

/**
 * One shaped glyph: [glyphId] is the font's glyph index after shaping and
 * [cluster] the index of the first code point it was shaped from.
 */
internal data class ShapedGlyph(
    val glyphId: Int,
    val cluster: Int,
    val offset: Float2D,
    val advance: Float2D,
)

/** A font's horizontal metrics in pixels; [lineGap] is typically zero. */
internal data class TextMetrics(
    val ascender: Float,
    val descender: Float,
    val lineGap: Float,
)
