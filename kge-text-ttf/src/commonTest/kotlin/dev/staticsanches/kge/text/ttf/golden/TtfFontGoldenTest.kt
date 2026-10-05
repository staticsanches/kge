package dev.staticsanches.kge.text.ttf.golden

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.golden.shouldMatchGolden
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.golden.canvas
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import dev.staticsanches.kge.text.ttf.KGETtfFontService
import dev.staticsanches.kge.text.ttf.robotoFontBytes
import io.kotest.core.spec.style.FunSpec

private const val SIZE_PX = 16
private const val TAB_SIZE = 4

/**
 * The configured TrueType font through `KGEFont.drawText`: the same eleven
 * scenes the legacy service composed, against the same committed references.
 */
@OptIn(KGESensitiveAPI::class)
class TtfFontGoldenTest :
    FunSpec({
        suspend fun configured(scope: ResourceScope): KGEFont =
            KGETtfFontService
                .createResources(scope, robotoFontBytes())
                .defaultFace
                .font(scope, SIZE_PX.fontPx)

        test("text/plain matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 24).use { target ->
                    font.drawText(target, 2, 2, "Ao", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/plain")
                }
            }
        }

        test("text/kerned matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 24).use { target ->
                    font.drawText(target, 2, 2, "AV", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/kerned")
                }
            }
        }

        test("text/tab-stop matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(32, 24).use { target ->
                    font.drawText(target, 2, 2, "A\tB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/tab-stop")
                }
            }
        }

        test("text/multiline matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 36).use { target ->
                    font.drawText(target, 2, 2, "A\nB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/multiline")
                }
            }
        }

        test("text/scale-2 matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(48, 32).use { target ->
                    font.drawText(target, 2, 2, "A", Colors.WHITE, 2, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/scale-2")
                }
            }
        }

        test("text/tint-opaque matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 24).use { target ->
                    font.drawText(
                        target, 2, 2, "A", Pixel.rgba(220, 30, 40, 255), 1, TAB_SIZE, Pixel.Mode.Normal,
                    )
                    target.shouldMatchGolden("text/tint-opaque")
                }
            }
        }

        test("text/tint-translucent matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 24).use { target ->
                    font.drawText(
                        target, 2, 2, "A", Pixel.rgba(220, 30, 40, 128), 1, TAB_SIZE, Pixel.Mode.Normal,
                    )
                    target.shouldMatchGolden("text/tint-translucent")
                }
            }
        }

        test("text/prefilled-opaque matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 24).use { target ->
                    target.fillPattern(255)
                    font.drawText(target, 2, 2, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/prefilled-opaque")
                }
            }
        }

        test("text/prefilled-translucent matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(30, 24).use { target ->
                    target.fillPattern(128)
                    font.drawText(target, 2, 2, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/prefilled-translucent")
                }
            }
        }

        test("text/clipped matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(9, 9).use { target ->
                    font.drawText(target, -4, -6, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/clipped")
                }
            }
        }

        test("text/mark matches the golden") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                canvas(16, 32).use { target ->
                    font.drawText(target, 2, 6, "q\u0323", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
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
