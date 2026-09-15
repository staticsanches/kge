package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

private val sample =
    listOf(
        BenchmarkResult(
            size = Int2D(320, 240),
            mode = "vsync",
            workload = "empty",
            highDpi = true,
            framebufferSize = Int2D(640, 480),
            metrics = BenchmarkMetrics(60.0, 59, 16.67),
        ),
        BenchmarkResult(
            size = Int2D(1920, 1080),
            mode = "uncapped",
            workload = "render",
            highDpi = false,
            framebufferSize = Int2D(1920, 1080),
            metrics = BenchmarkMetrics(214.5, 180, 4.66),
        ),
    )

class FormatTableTest :
    FunSpec({
        test("formats a header plus one row per result, carrying the numbers") {
            val lines = formatTable(sample).lines()

            lines.size shouldBe 3
            lines[0] shouldStartWith "Logical"
            lines[0] shouldContain "Physical"
            lines[0] shouldContain "Mode"
            lines[0] shouldContain "Workload"
            lines[0] shouldContain "HiDPI"
            lines[0] shouldContain "Avg FPS"
            lines[0] shouldContain "Min FPS"
            lines[0] shouldContain "ms/frame"
            lines[1] shouldContain "320x240"
            lines[1] shouldContain "640x480"
            lines[1] shouldContain "vsync"
            lines[1] shouldContain "empty"
            lines[1] shouldContain "on"
            lines[1] shouldContain "60.00"
            lines[1] shouldContain "59"
            lines[1] shouldContain "16.67"
            lines[2] shouldContain "1920x1080"
            lines[2] shouldContain "uncapped"
            lines[2] shouldContain "render"
            lines[2] shouldContain "off"
            lines[2] shouldContain "214.50"
            lines[2] shouldContain "180"
            lines[2] shouldContain "4.66"
        }

        test("renders the same rows as an HTML table") {
            val html = formatHtmlTable(sample)

            html shouldStartWith "<table><thead><tr>"
            html shouldContain "<th>Logical</th>"
            html shouldContain "<th>Workload</th>"
            html shouldContain "<th>HiDPI</th>"
            html shouldContain "<td>320x240</td>"
            html shouldContain "<td>empty</td>"
            html shouldContain "<td>on</td>"
            html shouldContain "<td>60.00</td>"
            html shouldContain "<td>uncapped</td>"
            html shouldContain "<td>render</td>"
            html shouldContain "<td>off</td>"
            html.endsWith("</tbody></table>") shouldBe true
        }

        test("escapes HTML-special characters in a cell") {
            val result =
                BenchmarkResult(
                    size = Int2D(1, 1),
                    mode = "a&b<c>",
                    workload = "render",
                    highDpi = false,
                    framebufferSize = Int2D(1, 1),
                    metrics = BenchmarkMetrics(1.0, 1, 1.0),
                )

            formatHtmlTable(listOf(result)) shouldContain "<td>a&amp;b&lt;c&gt;</td>"
        }
    })
