package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.axisValue
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private const val TAB_SIZE = 4
private const val SIZE_PX = 16

/**
 * The configured TrueType font's CPU draw: the scale no-op, the measured tab
 * stop, the coverage composite, the preserved Custom mode and the close rules.
 */
@OptIn(KGESensitiveAPI::class)
class TtfFontDrawTest :
    FunSpec({
        suspend fun configured(scope: ResourceScope): KGEFont =
            KGETtfFontService
                .createResources(scope, robotoFontBytes(), robotoItalicBytes())
                .defaultFace
                .font(scope, SIZE_PX.fontPx)

        test("a non-positive scale leaves the target untouched and skips the tab check") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite().use { target ->
                    font.drawText(target, 0, 0, "A", Colors.WHITE, 0, 0, Pixel.Mode.Normal)
                    font.drawText(target, 0, 0, "A", Colors.WHITE, -1, 0, Pixel.Mode.Normal)

                    target.alphaSum() shouldBe 0
                }

                emptySprite().use { target ->
                    val old = Pixel.rgba(20, 30, 40, 255)
                    target.clear(old)

                    font.drawText(target, 0, 0, "A", Colors.WHITE, 0, 0, Pixel.Mode.Normal)
                    font.drawText(target, 0, 0, "A", Colors.WHITE, -1, 0, Pixel.Mode.Normal)

                    target.pixels() shouldBe List(target.width * target.height) { old }
                }
            }
        }

        test("a tab lands on the measured stop and the ink stays inside the measured box") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                val box = font.measureText("A\tB", TAB_SIZE)
                box shouldBe Int2D(26, 19)

                emptySprite(width = 48, height = 24).use { walked ->
                    emptySprite(width = 48, height = 24).use { composed ->
                        font.drawText(walked, 0, 0, "A\tB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(composed, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        // the legacy pen pin: the stop puts B's pen at 15.875, drawn at round(15.875) = 16
                        font.drawText(composed, 16, 0, "B", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        walked.pixels() shouldBe composed.pixels()
                    }
                }

                emptySprite(width = box.x + 8, height = box.y + 8).use { target ->
                    font.drawText(target, 0, 0, "A\tB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                    var ink = 0
                    for (y in 0 until target.height) {
                        for (x in 0 until target.width) {
                            if (x >= box.x || y >= box.y) {
                                target.get(x, y) shouldBe Colors.TRANSPARENT
                            } else if (target.get(x, y).a > 0) {
                                ink++
                            }
                        }
                    }
                    (ink > 0) shouldBe true
                }
            }
        }

        test("a Custom mode receives the coverage-composited pixel and changes the result") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                val seen = mutableListOf<Pixel>()
                val custom =
                    object : Pixel.Mode.Custom {
                        override fun apply(
                            x: Int,
                            y: Int,
                            newPixel: Pixel,
                            oldPixel: Pixel,
                        ): Pixel {
                            oldPixel shouldBe Colors.TRANSPARENT
                            seen += newPixel
                            return Colors.RED
                        }
                    }

                emptySprite().use { target ->
                    emptySprite().use { plain ->
                        font.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, custom)
                        font.drawText(plain, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        val ink = plain.inkCells()
                        (ink.isNotEmpty()) shouldBe true
                        // the tap visits exactly the ink cells, with the coverage already folded into the alpha
                        seen.size shouldBe ink.size
                        seen.groupingBy { it }.eachCount() shouldBe
                            ink.map { plain.get(it.x, it.y) }.groupingBy { it }.eachCount()

                        for (y in 0 until target.height) {
                            for (x in 0 until target.width) {
                                target.get(x, y) shouldBe
                                    if (plain.get(x, y).a > 0) Colors.RED else Colors.TRANSPARENT
                            }
                        }
                        target.pixels() shouldNotBe plain.pixels()
                    }
                }
            }
        }

        test("a translucent tint scales the coverage and keeps the tint's color") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite().use { target ->
                    emptySprite().use { plain ->
                        font.drawText(target, 0, 0, "A", Pixel.rgba(255, 0, 0, 128), 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(plain, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        var full = false
                        for (y in 0 until target.height) {
                            for (x in 0 until target.width) {
                                val coverage = plain.get(x, y).a
                                val alpha = 128 * coverage / 255
                                if (coverage == 255) full = true
                                target.get(x, y) shouldBe
                                    if (alpha == 0) Colors.TRANSPARENT else Pixel.rgba(255, 0, 0, alpha)
                            }
                        }
                        full shouldBe true
                    }
                }
            }
        }

        test("the coverage composites over an opaque destination and leaves zero coverage alone") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                val old = Pixel.rgba(0, 0, 255)

                emptySprite().use { target ->
                    emptySprite().use { plain ->
                        target.clear(old)
                        font.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(plain, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        var full = false
                        var partial = false
                        var zero = false
                        for (y in 0 until target.height) {
                            for (x in 0 until target.width) {
                                val coverage = plain.get(x, y).a
                                val dest = target.get(x, y)
                                when (coverage) {
                                    0 -> {
                                        dest shouldBe old
                                        zero = true
                                    }

                                    255 -> {
                                        dest shouldBe Colors.WHITE
                                        full = true
                                    }

                                    else -> {
                                        dest shouldBe sourceOver(Colors.WHITE, coverage, old)
                                        dest.r shouldBe dest.g
                                        (dest.b > dest.r) shouldBe true
                                        dest.a shouldBe 255
                                        partial = true
                                    }
                                }
                            }
                        }
                        full shouldBe true
                        partial shouldBe true
                        zero shouldBe true
                    }
                }
            }
        }

        test("the coverage composites over a translucent destination") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                val old = Pixel.rgba(255, 0, 0, 128)

                emptySprite().use { target ->
                    emptySprite().use { plain ->
                        target.clear(old)
                        font.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(plain, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        var full = false
                        var partial = false
                        var zero = false
                        for (y in 0 until target.height) {
                            for (x in 0 until target.width) {
                                val coverage = plain.get(x, y).a
                                val dest = target.get(x, y)
                                when (coverage) {
                                    0 -> {
                                        dest shouldBe old
                                        zero = true
                                    }

                                    255 -> {
                                        dest shouldBe Colors.WHITE
                                        full = true
                                    }

                                    else -> {
                                        dest shouldBe sourceOver(Colors.WHITE, coverage, old)
                                        (dest.a >= old.a) shouldBe true
                                        partial = true
                                    }
                                }
                            }
                        }
                        full shouldBe true
                        partial shouldBe true
                        zero shouldBe true
                    }
                }
            }
        }

        test("a glyph partly outside the target is clipped to it") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite(width = 32, height = 24).use { reference ->
                    font.drawText(reference, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                    emptySprite(width = 32, height = 24).use { target ->
                        font.drawText(target, -5, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        assertClipped(reference, target, -5, 0)

                        // the visible left column is the reference's column 5, not 0
                        target.get(0, 3) shouldBe reference.get(5, 3)
                    }

                    emptySprite(width = 32, height = 24).use { target ->
                        font.drawText(target, 30, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        assertClipped(reference, target, 30, 0)
                    }

                    emptySprite(width = 32, height = 24).use { target ->
                        font.drawText(target, 0, 20, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        assertClipped(reference, target, 0, 20)
                    }
                }

                emptySprite(width = 32, height = 24).use { target ->
                    font.drawText(target, 100, 100, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    font.drawText(target, -20, -20, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                    target.alphaSum() shouldBe 0
                }
            }
        }

        test("drawing after the font is closed fails fast while the inert values answer") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                font.close()

                font.size.px shouldBe SIZE_PX
                font.axisCoordinates.getValue(KGEFont.Axis.Tag.Weight) shouldBe 400.axisValue

                emptySprite().use { target ->
                    shouldThrow<IllegalStateException> {
                        font.drawText(target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    }
                }

                shouldThrow<IllegalStateException> {
                    font.drawTextDecal(
                        Float2D(0f, 0f),
                        "A",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(32, 24),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) {}
                }
            }
        }

        test("the tab grid is measured from the line origin, not the draw origin") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite(width = 140, height = 24).use { reference ->
                    emptySprite(width = 140, height = 24).use { shifted ->
                        font.drawText(reference, 0, 0, "A\tB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(shifted, 100, 0, "A\tB", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        // the stop is 100 + 15.875, not the absolute grid multiple 111.125
                        assertClipped(reference, shifted, 100, 0)
                    }
                }
            }
        }

        test("the scale anchors on the line box, not the draw origin") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite(width = 48, height = 40).use { target ->
                    font.drawText(target, 2, 2, "A", Colors.WHITE, 2, TAB_SIZE, Pixel.Mode.Normal)

                    // (2 + round(0 * 2) + 0, 2 + round(14.84375 * 2) - 12 * 2) = (2, 8), 11x12 at 2x
                    target.inkCells().bounds() shouldBe (Int2D(2, 8) to Int2D(23, 31))
                    target.alphaSum() shouldBe 39932
                }
            }
        }

        test("scaled text advances by the scaled line height and the scaled advance") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite(width = 32, height = 72).use { one ->
                    emptySprite(width = 32, height = 72).use { two ->
                        font.drawText(one, 2, 2, "A", Colors.WHITE, 2, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(two, 2, 2, "A\nA", Colors.WHITE, 2, TAB_SIZE, Pixel.Mode.Normal)

                        // (2 + round((19 + 14.84375) * 2) - 24) = 46, one scaled line height below line 1
                        val second = two.diffFrom(one)
                        second.bounds() shouldBe (Int2D(2, 46) to Int2D(23, 69))
                        second.sumOf { two.get(it.x, it.y).a } shouldBe 39932
                    }
                }

                emptySprite(width = 48, height = 32).use { target ->
                    font.drawText(target, 0, 0, "AA", Colors.WHITE, 2, TAB_SIZE, Pixel.Mode.Normal)

                    // the second A at round(10.4375 * 2) = 21, so the pair spans 0..42
                    target.inkCells().bounds() shouldBe (Int2D(0, 6) to Int2D(42, 29))
                }
            }
        }

        test("a decomposed above mark keeps its shaped offset") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite().use { plain ->
                    emptySprite().use { paired ->
                        font.drawText(plain, 0, 0, "x", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(paired, 0, 0, "x\u0301", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        // (round(7.9375 + 0.453125) - 6, round(14.84375 + 0.078125) - 12)
                        val markCells = paired.diffFrom(plain)
                        markCells.bounds() shouldBe (Int2D(2, 3) to Int2D(6, 4))
                        markCells.sumOf { paired.get(it.x, it.y).a } shouldBe 736
                        (markCells.maxOf { it.y } < plain.inkCells().minOf { it.y }) shouldBe true
                    }
                }
            }
        }

        test("a below mark's negated offset lands under the base") {
            ResourceScope().use { scope ->
                val font = configured(scope)

                emptySprite().use { plain ->
                    emptySprite().use { paired ->
                        font.drawText(plain, 0, 0, "q", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                        font.drawText(paired, 0, 0, "q\u0323", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)

                        // (round(9.09375 + 2.734375) - 6, round(14.84375 + 3.171875) + 1) = (6, 19)
                        val markCells = paired.diffFrom(plain)
                        markCells.bounds() shouldBe (Int2D(6, 19) to Int2D(8, 20))
                        markCells.sumOf { paired.get(it.x, it.y).a } shouldBe 518
                        (markCells.minOf { it.y } > plain.inkCells().maxOf { it.y }) shouldBe true
                    }
                }
            }
        }
    })

/** A cleared surface; a failed construction closes it. */
private fun emptySprite(
    width: Int = 32,
    height: Int = 24,
): Sprite =
    SpriteService
        .create(width, height, Pixmap.SampleMode.NORMAL, "ttf font draw test")
        .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

private fun Pixmap.pixels(): List<Pixel> {
    val pixels = mutableListOf<Pixel>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels += get(x, y)
        }
    }
    return pixels
}

private fun Pixmap.alphaSum(): Int = pixels().sumOf { it.a }

/** The cells with ink, in reading order. */
private fun Pixmap.inkCells(): List<Int2D> {
    val cells = mutableListOf<Int2D>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (get(x, y).a > 0) cells += Int2D(x, y)
        }
    }
    return cells
}

/** The cells where this surface and [other] differ. */
private fun Pixmap.diffFrom(other: Pixmap): List<Int2D> {
    val cells = mutableListOf<Int2D>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (get(x, y) != other.get(x, y)) cells += Int2D(x, y)
        }
    }
    return cells
}

/** The top-left and bottom-right corners of [this] cells. */
private fun List<Int2D>.bounds(): Pair<Int2D, Int2D> =
    Int2D(minOf { it.x }, minOf { it.y }) to Int2D(maxOf { it.x }, maxOf { it.y })

/** The straight-alpha source-over of a coverage-weighted [tint] over [old], exact in Int. */
private fun sourceOver(
    tint: Pixel,
    coverage: Int,
    old: Pixel,
): Pixel {
    val weighted = tint.a * coverage / 255
    if (weighted == 0) return old
    if (old.a == 0) return Pixel.rgba(tint.r, tint.g, tint.b, weighted)
    val n = weighted * 255 + old.a * (255 - weighted)
    return Pixel.rgba(
        (tint.r * weighted * 255 + old.r * old.a * (255 - weighted)) / n,
        (tint.g * weighted * 255 + old.g * old.a * (255 - weighted)) / n,
        (tint.b * weighted * 255 + old.b * old.a * (255 - weighted)) / n,
        n / 255,
    )
}

/** Whether [clipped], drawn at ([dx], [dy]), is [reference] moved by that offset and cut to the surface. */
private fun assertClipped(
    reference: Pixmap,
    clipped: Pixmap,
    dx: Int,
    dy: Int,
) {
    clipped.width shouldBe reference.width
    clipped.height shouldBe reference.height
    for (y in 0 until clipped.height) {
        for (x in 0 until clipped.width) {
            val sx = x - dx
            val sy = y - dy
            val expected =
                if (sx in 0 until reference.width && sy in 0 until reference.height) {
                    reference.get(sx, sy)
                } else {
                    Colors.TRANSPARENT
                }
            clipped.get(x, y) shouldBe expected
        }
    }
}
