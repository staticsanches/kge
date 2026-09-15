package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.math.vector.Int2D
import kotlin.math.roundToLong

/**
 * The table cells of [results]: a header row followed by one row per result,
 * as unpadded strings.
 */
private fun tableCells(results: List<BenchmarkResult>): List<List<String>> {
    val header =
        listOf(LOGICAL_HEADER, PHYSICAL_HEADER, MODE_HEADER, HIGH_DPI_HEADER, FPS_HEADER, MIN_HEADER, MS_HEADER)
    val rows =
        results.map { result ->
            listOf(
                result.size.label(),
                result.framebufferSize.label(),
                result.mode,
                if (result.highDpi) ON else OFF,
                result.metrics.avgFps.fixed2(),
                result.metrics.minFps.toString(),
                result.metrics.msPerFrame.fixed2(),
            )
        }
    return listOf(header) + rows
}

/** Renders [results] as a fixed-width text table: text columns left, numbers right. */
internal fun formatTable(results: List<BenchmarkResult>): String {
    val cells = tableCells(results)
    val widths = cells.first().indices.map { column -> cells.maxOf { it[column].length } }
    return cells.joinToString("\n") { row ->
        row
            .mapIndexed { column, cell ->
                if (column <= LAST_TEXT_COLUMN) cell.padEnd(widths[column]) else cell.padStart(widths[column])
            }.joinToString(" ")
    }
}

/** Renders [results] as an HTML `<table>`, same rows as [formatTable]. */
internal fun formatHtmlTable(results: List<BenchmarkResult>): String {
    val cells = tableCells(results)
    val header = cells.first()
    val body = cells.drop(1)
    return buildString {
        append("<table><thead><tr>")
        header.forEach { append("<th>").append(it.escapeHtml()).append("</th>") }
        append("</tr></thead><tbody>")
        body.forEach { row ->
            append("<tr>")
            row.forEach { append("<td>").append(it.escapeHtml()).append("</td>") }
            append("</tr>")
        }
        append("</tbody></table>")
    }
}

private fun Int2D.label(): String = "${x}x$y"

/** Formats with exactly two decimals without a locale-dependent `String.format`. */
private fun Double.fixed2(): String {
    val scaled = (this * 100).roundToLong()
    val fraction = (scaled % 100).let { if (it < 0) -it else it }
    return "${scaled / 100}.${fraction.toString().padStart(2, '0')}"
}

private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** The text columns (logical, physical, mode, HiDPI) before the right-aligned numbers. */
private const val LAST_TEXT_COLUMN = 3

private const val LOGICAL_HEADER = "Logical"
private const val PHYSICAL_HEADER = "Physical"
private const val MODE_HEADER = "Mode"
private const val HIGH_DPI_HEADER = "HiDPI"
private const val FPS_HEADER = "Avg FPS"
private const val MIN_HEADER = "Min FPS"
private const val MS_HEADER = "ms/frame"
private const val ON = "on"
private const val OFF = "off"
