package dev.staticsanches.kge.text.ttf.golden

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.golden.shouldMatchGolden
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.testsupport.golden.canvas
import dev.staticsanches.kge.text.ttf.Font
import dev.staticsanches.kge.text.ttf.TtfTextService
import io.kotest.core.spec.style.FunSpec

/**
 * The composed CPU text draw against the committed references: placement,
 * kerning, tab stops, line boxes, scale, tinting, compositing and clipping.
 */
class TextGoldenTest :
    FunSpec({
        test("text/plain matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 24).use { target ->
                    TtfTextService.drawString(font, target, 2, 2, "Ao", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/plain")
                }
            }
        }

        test("text/kerned matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 24).use { target ->
                    TtfTextService.drawString(font, target, 2, 2, "AV", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/kerned")
                }
            }
        }

        test("text/tab-stop matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(32, 24).use { target ->
                    TtfTextService.drawString(font, target, 2, 2, "A\tB", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/tab-stop")
                }
            }
        }

        test("text/multiline matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 36).use { target ->
                    TtfTextService.drawString(font, target, 2, 2, "A\nB", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/multiline")
                }
            }
        }

        test("text/scale-2 matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(48, 32).use { target ->
                    TtfTextService.drawString(font, target, 2, 2, "A", 16, Colors.WHITE, 2, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/scale-2")
                }
            }
        }

        test("text/tint-opaque matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 24).use { target ->
                    TtfTextService.drawString(
                        font, target, 2, 2, "A", 16, Pixel.rgba(220, 30, 40, 255), 1, 4, Pixel.Mode.Normal,
                    )
                    target.shouldMatchGolden("text/tint-opaque")
                }
            }
        }

        test("text/tint-translucent matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 24).use { target ->
                    TtfTextService.drawString(
                        font, target, 2, 2, "A", 16, Pixel.rgba(220, 30, 40, 128), 1, 4, Pixel.Mode.Normal,
                    )
                    target.shouldMatchGolden("text/tint-translucent")
                }
            }
        }

        test("text/prefilled-opaque matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 24).use { target ->
                    target.fillPattern(255)
                    TtfTextService.drawString(font, target, 2, 2, "A", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/prefilled-opaque")
                }
            }
        }

        test("text/prefilled-translucent matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(30, 24).use { target ->
                    target.fillPattern(128)
                    TtfTextService.drawString(font, target, 2, 2, "A", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/prefilled-translucent")
                }
            }
        }

        test("text/clipped matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(9, 9).use { target ->
                    TtfTextService.drawString(font, target, -4, -6, "A", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/clipped")
                }
            }
        }

        test("text/mark matches the golden") {
            Font.load(Roboto.variableFont).use { font ->
                canvas(16, 32).use { target ->
                    TtfTextService.drawString(font, target, 2, 6, "q\u0323", 16, Colors.WHITE, 1, 4, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/mark")
                }
            }
        }
    })

private fun Pixmap.Mutable.fillPattern(alpha: Int) {
    for (y in 0 until height) {
        for (x in 0 until width) {
            set(x, y, Pixel.rgba(20 + 15 * x, 30 + 20 * y, 200 - 10 * (x + y), alpha))
        }
    }
}
