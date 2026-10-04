package dev.staticsanches.kge.text

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asSequence
import dev.staticsanches.kge.rasterizer.service.DrawService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.golden.canvas
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4

/**
 * The CPU text draw: olc's glyph walk, its newline/tab advances under the
 * effective scale, the olc pixel mode resolution and the scale no-op.
 */
@OptIn(KGESensitiveAPI::class)
class CoreFontDrawTest :
    FunSpec({
        suspend fun withFamily(block: suspend (ResourceScope, KGECoreFontFamily) -> Unit) {
            installGl()
            ResourceScope().use { scope -> block(scope, KGECoreFontService.createResources(scope)) }
        }

        suspend fun KGEFont.Family.mono(
            scope: ResourceScope,
            px: Int = 8,
        ): KGEFont = defaultFace.font(scope, px.fontPx)

        suspend fun KGECoreFontFamily.prop(
            scope: ResourceScope,
            px: Int = 8,
        ): KGEFont = proportional.font(scope, px.fontPx)

        test("mono draw resets x and advances y by 8 * effectiveScale on a newline") {
            withFamily { scope, family ->
                for ((base, scale) in listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2)) {
                    val glyph = 8 * base * scale
                    assertSecondGlyph(
                        family.mono(scope, 8 * base),
                        scale,
                        "A\nB",
                        glyph,
                        2 * glyph,
                        "A",
                        "B",
                        0,
                        glyph,
                    )
                }
            }
        }

        test("mono draw advances x by 8 * tabSizeInSpaces * effectiveScale on a tab") {
            withFamily { scope, family ->
                for ((base, scale) in listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2)) {
                    val glyph = 8 * base * scale
                    val tab = 8 * TAB_SIZE * base * scale
                    assertSecondGlyph(
                        family.mono(scope, 8 * base),
                        scale,
                        "A\tB",
                        glyph + tab + glyph,
                        glyph,
                        "A",
                        "B",
                        glyph + tab,
                        0,
                    )
                }
            }
        }

        test("mono draw paints a carriage return exactly as a space: one blank cell between the glyphs") {
            withFamily { scope, family ->
                val mono = family.mono(scope)
                canvas(24, 8).use { carriageReturn ->
                    canvas(24, 8).use { space ->
                        mono.drawText(carriageReturn, 0, 0, "A\rB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        mono.drawText(space, 0, 0, "A B", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        assertSamePixels(carriageReturn, space)
                    }
                }
            }
        }

        test("the pixel mode resolves as olc: opaque to Mask, translucent to Alpha, Custom kept identical") {
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
            withFamily { scope, family ->
                val mono = family.mono(scope)
                canvas(8, 8).use { target ->
                    mono.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    modes.toSet() shouldBe setOf<Pixel.Mode>(Pixel.Mode.Mask)

                    modes.clear()
                    val translucent = Pixel.rgba(255, 255, 255, 128)
                    mono.drawText(target, 0, 0, "A", translucent, 1, TAB_SIZE, Pixel.Mode.Normal)
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
                    mono.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, custom)
                    modes.toSet() shouldBe setOf<Pixel.Mode>(custom)
                    (modes.isNotEmpty() && modes.all { it === custom }) shouldBe true
                }
            }
        }

        test("a translucent color blends over the stored pixel in Alpha") {
            withFamily { scope, family ->
                val mono = family.mono(scope)
                canvas(8, 8).use { target ->
                    target.clear(Colors.BLACK)
                    val translucent = Pixel.rgba(255, 255, 255, 128)
                    mono.drawText(target, 0, 0, "A", translucent, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.get(2, 0) shouldBe Pixel.rgba(128, 128, 128)
                    target.get(0, 0) shouldBe Colors.BLACK
                }
            }
        }

        test("an opaque color paints the set cells and leaves the clear cells untouched") {
            withFamily { scope, family ->
                val mono = family.mono(scope)
                canvas(8, 8).use { target ->
                    mono.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    target.get(2, 0) shouldBe Colors.WHITE
                    target.get(0, 0) shouldBe Colors.TRANSPARENT
                    target.get(1, 7) shouldBe Colors.TRANSPARENT
                }
            }
        }

        test("a non-positive scale leaves the target untouched and does not validate the tab size") {
            withFamily { scope, family ->
                val mono = family.mono(scope)
                canvas(16, 8).use { target ->
                    target.clear(Colors.RED)
                    mono.drawText(target, 0, 0, "Hi", Colors.WHITE, 0, TAB_SIZE, Pixel.Mode.Normal)
                    mono.drawText(target, 0, 0, "Hi", Colors.WHITE, -1, TAB_SIZE, Pixel.Mode.Normal)
                    mono.drawText(target, 0, 0, "Hi", Colors.WHITE, 0, 0, Pixel.Mode.Normal)
                    target.asSequence().toList() shouldBe List(target.width * target.height) { Colors.RED }
                }
            }
        }
    })

private fun assertSecondGlyph(
    font: KGEFont,
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
            font.drawText(actual, 0, 0, text, Colors.WHITE, scale, TAB_SIZE, Pixel.Mode.Normal)
            font.drawText(expected, 0, 0, first, Colors.WHITE, scale, TAB_SIZE, Pixel.Mode.Normal)
            font.drawText(expected, secondX, secondY, second, Colors.WHITE, scale, TAB_SIZE, Pixel.Mode.Normal)
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
