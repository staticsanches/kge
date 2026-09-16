package dev.staticsanches.kge.text

import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.golden.canvas
import dev.staticsanches.kge.golden.shouldMatchGolden
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asSequence
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4

/**
 * The CPU text draw: olc's glyph walk into a `Pixmap.Mutable` (mono and
 * proportional, scaled and unscaled), its newline/tab advances, the olc pixel
 * mode resolution and the scale no-op. The goldens are authored from an
 * independent olc-derived port.
 */
class DrawStringTest :
    FunSpec({
        fun newScope(): ResourceScope {
            installGl()
            return ResourceScope().also { DrawStringService.createResources(it) }
        }

        test("mono draw at scale 1 matches the olc-derived golden") {
            newScope().use { scope ->
                canvas(16, 8).use { target ->
                    drawMono(target, scope, 0, 0, "Hi", 1)
                    target.shouldMatchGolden("text/mono")
                }
            }
        }

        test("mono draw at scale 2 matches the olc-derived golden") {
            newScope().use { scope ->
                canvas(32, 16).use { target ->
                    drawMono(target, scope, 0, 0, "Hi", 2)
                    target.shouldMatchGolden("text/mono-scale-2")
                }
            }
        }

        test("prop draw at scale 1 matches the olc-derived golden") {
            newScope().use { scope ->
                canvas(11, 8).use { target ->
                    DrawStringService
                        .drawStringProp(scope, target, 0, 0, "Hi", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/prop")
                }
            }
        }

        test("prop draw at scale 2 matches the olc-derived golden") {
            newScope().use { scope ->
                canvas(22, 16).use { target ->
                    DrawStringService
                        .drawStringProp(scope, target, 0, 0, "Hi", Colors.WHITE, 2, TAB_SIZE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("text/prop-scale-2")
                }
            }
        }

        test("the Int2D overloads agree with the raw forms") {
            newScope().use { scope ->
                canvas(16, 8).use { typed ->
                    canvas(16, 8).use { raw ->
                        DrawStringService.drawString(
                            scope, typed, Int2D(1, 1), "Hi", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal,
                        )
                        DrawStringService.drawString(
                            scope, raw, 1, 1, "Hi", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal,
                        )
                        assertSamePixels(typed, raw)

                        DrawStringService.drawStringProp(
                            scope, typed, Int2D(1, 1), "Hi", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal,
                        )
                        DrawStringService.drawStringProp(
                            scope, raw, 1, 1, "Hi", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal,
                        )
                        assertSamePixels(typed, raw)
                    }
                }
            }
        }

        test("mono draw resets x and advances y by 8*scale on a newline") {
            newScope().use { scope ->
                for (scale in 1..2) {
                    val glyph = 8 * scale
                    assertSecondGlyph(scope, scale, "A\nB", glyph, 2 * glyph, "A", "B", 0, glyph)
                }
            }
        }

        test("mono draw advances x by 8*tabSizeInSpaces*scale on a tab") {
            newScope().use { scope ->
                for (scale in 1..2) {
                    val glyph = 8 * scale
                    val tab = 8 * TAB_SIZE * scale
                    assertSecondGlyph(scope, scale, "A\tB", glyph + tab + glyph, glyph, "A", "B", glyph + tab, 0)
                }
            }
        }

        test("the pixel mode resolves as olc: opaque to Mask, translucent to Alpha, Custom kept") {
            val modes = mutableListOf<Pixel.Mode>()
            DrawService.override(
                object : DrawService {
                    override fun draw(
                        target: Pixmap.Mutable,
                        x: Int,
                        y: Int,
                        color: Pixel,
                        mode: Pixel.Mode,
                    ): Boolean {
                        modes += mode
                        return DrawService.original.draw(target, x, y, color, mode)
                    }
                },
            )
            newScope().use { scope ->
                canvas(8, 8).use { target ->
                    DrawStringService
                        .drawString(scope, target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    modes.toSet() shouldBe setOf<Pixel.Mode>(Pixel.Mode.Mask)

                    modes.clear()
                    val translucent = Pixel.rgba(255, 255, 255, 128)
                    DrawStringService
                        .drawString(scope, target, 0, 0, "A", translucent, 1, TAB_SIZE, Pixel.Mode.Normal)
                    modes.toSet() shouldBe setOf<Pixel.Mode>(Pixel.Mode.Alpha())

                    modes.clear()
                    val custom =
                        object : Pixel.Mode.Custom {
                            override fun apply(
                                x: Int,
                                y: Int,
                                newPixel: Pixel,
                                oldPixel: Pixel,
                            ): Pixel = newPixel
                        }
                    DrawStringService.drawString(scope, target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, custom)
                    modes.toSet() shouldBe setOf<Pixel.Mode>(custom)
                }
            }
        }

        test("a translucent color blends over the stored pixel in Alpha") {
            newScope().use { scope ->
                canvas(8, 8).use { target ->
                    target.clear(Colors.BLACK)
                    val translucent = Pixel.rgba(255, 255, 255, 128)
                    DrawStringService
                        .drawString(scope, target, 0, 0, "A", translucent, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.get(2, 0) shouldBe Pixel.rgba(128, 128, 128)
                    target.get(0, 0) shouldBe Colors.BLACK
                }
            }
        }

        test("an opaque color paints the set cells and leaves the clear cells untouched") {
            newScope().use { scope ->
                canvas(8, 8).use { target ->
                    drawMono(target, scope, 0, 0, "A", 1)
                    target.get(2, 0) shouldBe Colors.WHITE
                    target.get(0, 0) shouldBe Colors.TRANSPARENT
                    target.get(1, 7) shouldBe Colors.TRANSPARENT
                }
            }
        }

        test("a non-positive scale leaves the target untouched") {
            newScope().use { scope ->
                canvas(16, 8).use { target ->
                    target.clear(Colors.RED)
                    DrawStringService
                        .drawString(scope, target, 0, 0, "Hi", Colors.WHITE, 0, TAB_SIZE, Pixel.Mode.Normal)
                    DrawStringService
                        .drawStringProp(scope, target, 0, 0, "Hi", Colors.WHITE, -1, TAB_SIZE, Pixel.Mode.Normal)
                    target.asSequence().toList() shouldBe List(target.width * target.height) { Colors.RED }
                }
            }
        }
    })

private fun drawMono(
    target: Pixmap.Mutable,
    scope: ResourceScope,
    x: Int,
    y: Int,
    text: String,
    scale: Int,
) {
    DrawStringService
        .drawString(scope, target, x, y, text, Colors.WHITE, scale, TAB_SIZE, Pixel.Mode.Normal)
}

private fun assertSecondGlyph(
    scope: ResourceScope,
    scale: Int,
    text: String,
    width: Int,
    height: Int,
    first: String,
    second: String,
    secondX: Int,
    secondY: Int,
) {
    canvas(width, height).use { actual ->
        canvas(width, height).use { expected ->
            drawMono(actual, scope, 0, 0, text, scale)
            drawMono(expected, scope, 0, 0, first, scale)
            drawMono(expected, scope, secondX, secondY, second, scale)
            assertSamePixels(actual, expected)
        }
    }
}

private fun assertSamePixels(
    actual: Pixmap,
    expected: Pixmap,
) {
    actual.width shouldBe expected.width
    actual.height shouldBe expected.height
    actual.asSequence().toList() shouldBe expected.asSequence().toList()
}
