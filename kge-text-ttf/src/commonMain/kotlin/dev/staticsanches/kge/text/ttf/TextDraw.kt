package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.service.BlitService
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Walks [text] from the line box top-left ([x], [y]) and reports every shaped
 * glyph at its float baseline pen; returns the box the text occupies.
 */
internal inline fun Font.walkText(
    text: String,
    sizePx: Int,
    tabSizeInSpaces: Int,
    x: Int,
    y: Int,
    place: (glyph: ShapedGlyph, penX: Float, penY: Float) -> Unit,
): Int2D {
    check(tabSizeInSpaces > 0) { "Invalid tab size: $tabSizeInSpaces" }

    // The metrics of an empty run carry the line box, so a text of tabs only still has one.
    val metrics = shape("", sizePx).metrics
    val lineHeight = ceil(metrics.ascender - metrics.descender + metrics.lineGap).toInt()
    val originX = x.toFloat()

    var spaceAdvance = 0f
    var spaceShaped = false
    var widest = 0f
    var lines = 1
    var lineTop = y.toFloat()

    var lineStart = 0
    while (true) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        var penX = 0f
        var segmentStart = lineStart
        while (true) {
            val tab = text.indexOf('\t', segmentStart)
            val segmentEnd = if (tab in segmentStart until lineEnd) tab else lineEnd
            if (segmentEnd > segmentStart) {
                shape(text.substring(segmentStart, segmentEnd), sizePx).glyphs.forEach { glyph ->
                    place(glyph, originX + penX, lineTop + metrics.ascender)
                    penX += glyph.advance.x
                    if (penX > widest) widest = penX
                }
            }
            if (segmentEnd >= lineEnd) break
            if (!spaceShaped) {
                val space = shape(" ", sizePx).glyphs.single()
                spaceAdvance = space.advance.x
                spaceShaped = true
            }
            val step = tabSizeInSpaces * spaceAdvance
            penX = (floor(penX / step) + 1) * step
            if (penX > widest) widest = penX
            segmentStart = segmentEnd + 1
        }
        if (newline < 0) break
        lines++
        lineTop += lineHeight
        lineStart = newline + 1
    }
    return Int2D(ceil(widest).toInt(), lines * lineHeight)
}

/** The pixel box of [text] at [sizePx] with tab stops of [tabSizeInSpaces] spaces. */
internal fun measureText(
    font: Font,
    text: String,
    sizePx: Int,
    tabSizeInSpaces: Int,
): Int2D = font.walkText(text, sizePx, tabSizeInSpaces, 0, 0) { _, _, _ -> }

/**
 * Blits [text] into [target] from the ([x], [y]) line-box top-left, tinted by
 * [color] and scaled by [scale]; only [Pixel.Mode.Custom] changes the blend.
 */
internal fun drawText(
    font: Font,
    target: Pixmap.Mutable,
    x: Int,
    y: Int,
    text: String,
    sizePx: Int,
    color: Pixel,
    scale: Int,
    tabSizeInSpaces: Int,
    mode: Pixel.Mode,
) {
    if (scale <= 0) return

    val tint = CoverageTint(color, mode)
    var charts: List<Sprite> = emptyList()
    font.walkText(text, sizePx, tabSizeInSpaces, x, y) { glyph, penX, penY ->
        val placed = font.glyph(sizePx, glyph.glyphId)
        if (placed is AtlasGlyph.Placed) {
            // Charts only grow, so the snapshot is refreshed only when one is added.
            if (placed.chartIndex >= charts.size) {
                charts = font.atlas(sizePx)!!.charts
            }
            BlitService.blitRegion(
                target,
                // HarfBuzz offsets are y-up, the raster y-down.
                ((penX + glyph.offset.x) * scale).roundToInt() + placed.bearing.x * scale,
                ((penY - glyph.offset.y) * scale).roundToInt() + placed.bearing.y * scale,
                charts[placed.chartIndex],
                placed.source,
                placed.size,
                scale,
                Pixmap.Flip.NONE,
                tint,
            )
        }
    }
}

/** Folds a glyph's coverage into the tint's alpha, then resolves the caller's mode. */
private class CoverageTint(
    private val tint: Pixel,
    private val mode: Pixel.Mode,
) : Pixel.Mode.Custom {
    override fun apply(
        x: Int,
        y: Int,
        newPixel: Pixel,
        oldPixel: Pixel,
    ): Pixel {
        // The seam is tapped only where the glyph has ink, olc's own draw rule.
        if (newPixel.a == 0) return oldPixel

        val weighted = Pixel.rgba(tint.r, tint.g, tint.b, tint.a * newPixel.a / 255)
        return if (mode is Pixel.Mode.Custom) mode.apply(x, y, weighted, oldPixel) else weighted.srcOver(oldPixel)
    }
}

/** The source-over composite of a straight-alpha [Pixel] onto [oldPixel]. */
private fun Pixel.srcOver(oldPixel: Pixel): Pixel {
    if (a == 0) return oldPixel
    if (oldPixel.a == 0) return this

    val source = a / 255f
    val destination = oldPixel.a / 255f
    val inverse = 1f - source
    val outAlpha = source + destination * inverse
    return Pixel.rgba(
        ((r * source + oldPixel.r * destination * inverse) / outAlpha).toInt(),
        ((g * source + oldPixel.g * destination * inverse) / outAlpha).toInt(),
        ((b * source + oldPixel.b * destination * inverse) / outAlpha).toInt(),
        (outAlpha * 255f).toInt(),
    )
}
