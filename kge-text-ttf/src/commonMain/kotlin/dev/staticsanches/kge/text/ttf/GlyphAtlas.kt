package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.KGEResource

/** A glyph's atlas entry: where its ink lives on a chart, or nothing to draw. */
internal sealed interface AtlasGlyph {
    /** A glyph with no ink (space, control): nothing to draw. */
    data object Blank : AtlasGlyph

    data class Placed(
        val chartIndex: Int,
        val source: Int2D,
        val size: Int2D,
        val bearing: Int2D,
    ) : AtlasGlyph
}

/**
 * The white-alpha glyph cache of one pixel size: each glyph is rasterized once
 * and packed into shelf rows across 512x512 charts.
 */
internal class GlyphAtlas internal constructor(
    private val sizePx: Int,
    private val rasterize: (glyphId: Int) -> GlyphCoverage,
) : KGEResource {
    private val chartList = mutableListOf<Chart>()
    private val glyphsByGlyphId = mutableMapOf<Int, AtlasGlyph>()
    private var closed = false

    internal val charts: List<Sprite>
        get() = chartList.map { it.sprite }

    /** The glyph's cached entry, rasterizing and packing it on first use. */
    internal fun glyph(glyphId: Int): AtlasGlyph {
        check(!closed) { "the glyph atlas has been released and can not be used" }
        glyphsByGlyphId[glyphId]?.let { return it }

        val coverage = rasterize(glyphId)
        val glyph = if (coverage.coverage.isEmpty()) AtlasGlyph.Blank else place(coverage)
        glyphsByGlyphId[glyphId] = glyph
        return glyph
    }

    private fun place(coverage: GlyphCoverage): AtlasGlyph.Placed {
        require(coverage.width <= CHART_SIZE && coverage.height <= CHART_SIZE) {
            "glyph coverage ${coverage.width}x${coverage.height} does not fit a ${CHART_SIZE}px chart"
        }

        chartList.forEachIndexed { index, chart ->
            chart.firstFittingRow(coverage.width, coverage.height)?.let { row ->
                return placed(chart, index, row, coverage)
            }
        }
        chartList.forEachIndexed { index, chart ->
            chart.appendRow(coverage.height)?.let { row ->
                return placed(chart, index, row, coverage)
            }
        }

        val chart = newChart()
        val row = chart.appendRow(coverage.height) ?: error("glyph coverage does not fit a ${CHART_SIZE}px chart")
        return placed(chart, chartList.lastIndex, row, coverage)
    }

    private fun placed(
        chart: Chart,
        index: Int,
        row: Row,
        coverage: GlyphCoverage,
    ): AtlasGlyph.Placed {
        val source = Int2D(row.x, row.y)
        row.x += coverage.width
        chart.sprite.writeCoverage(source, coverage)
        return AtlasGlyph.Placed(index, source, Int2D(coverage.width, coverage.height), coverage.bearing)
    }

    private fun newChart(): Chart =
        Chart(
            SpriteService.create(
                CHART_SIZE,
                CHART_SIZE,
                Pixmap.SampleMode.NORMAL,
                "glyph atlas (${sizePx}px) #${chartList.size}",
            ),
        ).also { chartList += it }

    override fun close() {
        if (closed) return
        closed = true
        glyphsByGlyphId.clear()
        val sheets = chartList.map { it.sprite }
        chartList.clear()
        sheets.closeAll()
    }

    private class Chart(
        val sprite: Sprite,
    ) {
        private val rows = mutableListOf<Row>()

        fun firstFittingRow(
            width: Int,
            height: Int,
        ): Row? = rows.firstOrNull { it.fits(width, height) }

        fun appendRow(height: Int): Row? {
            val y = rows.lastOrNull()?.let { it.y + it.height } ?: 0
            if (y + height > CHART_SIZE) return null
            return Row(y, height).also { rows += it }
        }
    }

    private class Row(
        val y: Int,
        val height: Int,
    ) {
        var x: Int = 0

        fun fits(
            width: Int,
            height: Int,
        ): Boolean = height <= this.height && width <= CHART_SIZE - x
    }
}

private const val CHART_SIZE = 512

/** Writes the coverage box as opaque white with the byte as alpha, row-major. */
private fun Sprite.writeCoverage(
    source: Int2D,
    coverage: GlyphCoverage,
) {
    for (row in 0 until coverage.height) {
        for (column in 0 until coverage.width) {
            val alpha = coverage.coverage[row * coverage.width + column].toInt() and 0xFF
            uncheckedSet(
                source.x + column,
                source.y + row,
                when (alpha) {
                    0 -> Colors.TRANSPARENT
                    255 -> Colors.WHITE
                    else -> Pixel.rgba(255, 255, 255, alpha)
                },
            )
        }
    }
}

/**
 * Closes every element, keeping going past a failure: the first throwable is
 * rethrown and the later ones are added to it as suppressed.
 */
internal fun <T : KGEResource> Iterable<T>.closeAll() {
    var failure: Throwable? = null
    for (element in this) {
        try {
            element.close()
        } catch (e: Throwable) {
            val first = failure
            if (first == null) failure = e else first.addSuppressed(e)
        }
    }
    failure?.let { throw it }
}
