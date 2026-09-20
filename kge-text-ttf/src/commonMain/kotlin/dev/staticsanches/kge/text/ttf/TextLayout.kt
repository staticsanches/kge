package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.math.vector.Float2D

/**
 * One shaped glyph: [glyphId] is the font's glyph index after shaping and
 * [cluster] the index of the first code point it was shaped from.
 */
data class ShapedGlyph(
    val glyphId: Int,
    val cluster: Int,
    val offset: Float2D,
    val advance: Float2D,
)

/** A single shaped run: its glyphs in visual order and the metrics of the size it was shaped at. */
data class ShapedRun(
    val glyphs: List<ShapedGlyph>,
    val metrics: TextMetrics,
)

/** A font's horizontal metrics in pixels; [lineGap] is typically zero. */
data class TextMetrics(
    val ascender: Float,
    val descender: Float,
    val lineGap: Float,
)
