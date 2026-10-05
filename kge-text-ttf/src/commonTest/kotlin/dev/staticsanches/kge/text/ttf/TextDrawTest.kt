package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * The CPU text draw: the pen walk and its box, the coverage blit into a mutable
 * pixmap, pen placement, scale, no-ops, clipping and the atlas cache, pinned on
 * the shipped Roboto fixture.
 */
class TextDrawTest :
    FunSpec({
        test("the walk reports the pinned 16 px box of a single line") {
            // the fixture identity, so a font bump fails here instead of re-pinning
            Roboto.FAMILY shouldBe "Roboto"
            Roboto.VERSION shouldBe "3.015"

            Font.load(Roboto.romanFont).use { font ->
                TtfTextService.getTextSize(font, "", 16, 4) shouldBe Int2D(0, 19)
                TtfTextService.getTextSize(font, "A", 16, 4) shouldBe Int2D(11, 19)
                TtfTextService.getTextSize(font, " ", 16, 4) shouldBe Int2D(4, 19)
                TtfTextService.getTextSize(font, "AAAA", 16, 4) shouldBe Int2D(42, 19)
            }
        }

        test("a newline advances the line height and the box takes the widest line") {
            Font.load(Roboto.romanFont).use { font ->
                TtfTextService.getTextSize(font, "A\nB", 16, 4) shouldBe Int2D(11, 38)
                TtfTextService.getTextSize(font, "AAAA\nA", 16, 4) shouldBe Int2D(42, 38)
                TtfTextService.getTextSize(font, "A\nAAAA", 16, 4) shouldBe Int2D(42, 38)
                TtfTextService.getTextSize(font, "A", 32, 4) shouldBe Int2D(21, 38)
            }
        }

        test("a tab is a stop on the line grid, not a fixed step") {
            Font.load(Roboto.romanFont).use { font ->
                // the isolated space advance makes a four-space step 15.875
                val space = font.shape(" ", 16).glyphs.single()
                space.advance.x shouldBe 3.96875f

                font.pensOf("A\tB") shouldBe
                    listOf(Float2D(0f, 14.84375f), Float2D(15.875f, 14.84375f))
                // the same grid from a further pen: 41.75 is past the second stop
                font.pensOf("AAAA\tB").last().x shouldBe 47.625f

                TtfTextService.getTextSize(font, "A\tB", 16, 4) shouldBe Int2D(26, 19)
                TtfTextService.getTextSize(font, "AAAA\tB", 16, 4) shouldBe Int2D(58, 19)
            }
        }

        test("the tab grid is measured from the line origin, not the target origin") {
            Font.load(Roboto.romanFont).use { font ->
                val pens = mutableListOf<Float2D>()
                val box = font.walkText("A\tB", 16, 4, 100, 0) { _, penX, penY -> pens += Float2D(penX, penY) }

                // x + 15.875, not the absolute grid multiple 111.125
                pens shouldBe listOf(Float2D(100f, 14.84375f), Float2D(115.875f, 14.84375f))
                box shouldBe Int2D(26, 19)
            }
        }

        test("measuring never rasterizes") {
            Font.load(Roboto.romanFont).use { font ->
                TtfTextService.getTextSize(font, "AV", 16, 4)

                font.atlas(16) shouldBe null
            }
        }

        test("the blit places the pinned box and composites the coverage over a transparent target") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")
                placed shouldBe AtlasGlyph.Placed(0, Int2D(0, 0), Int2D(11, 12), Int2D(0, -12))

                emptySprite().use { target ->
                    draw(font, target, 0, 0, "A")

                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    var sum = 0
                    var full = false
                    var zero = false
                    for (y in 0 until placed.size.y) {
                        for (x in 0 until placed.size.x) {
                            val coverage = chart.get(placed.source.x + x, placed.source.y + y).a
                            sum += coverage
                            if (coverage == 255) full = true
                            if (coverage == 0) zero = true
                            target.get(x, 3 + y) shouldBe
                                if (coverage == 0) Colors.TRANSPARENT else Pixel.rgba(255, 255, 255, coverage)
                        }
                    }
                    sum shouldBe 9983
                    // the box really holds both extremes, so the two branches above are not vacuous
                    full shouldBe true
                    zero shouldBe true
                    // nothing outside the placed box: (0, round(14.84375) - 12) = (0, 3)
                    for (y in 0 until target.height) {
                        for (x in 0 until target.width) {
                            if (x >= 11 || y < 3 || y >= 15) target.get(x, y) shouldBe Colors.TRANSPARENT
                        }
                    }
                }
            }
        }

        test("a translucent tint scales the coverage and keeps the tint's color") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")

                emptySprite().use { target ->
                    draw(font, target, 0, 0, "A", color = Pixel.rgba(255, 0, 0, 128))

                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    var full = false
                    for (y in 0 until placed.size.y) {
                        for (x in 0 until placed.size.x) {
                            val coverage = chart.get(placed.source.x + x, placed.source.y + y).a
                            val alpha = 128 * coverage / 255
                            if (coverage == 255) full = true
                            target.get(x, 3 + y) shouldBe
                                if (alpha == 0) Colors.TRANSPARENT else Pixel.rgba(255, 0, 0, alpha)
                        }
                    }
                    full shouldBe true
                }
            }
        }

        test("the coverage composites over an opaque destination and leaves zero coverage alone") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")
                val old = Pixel.rgba(0, 0, 255)

                emptySprite().use { target ->
                    target.clear(old)
                    draw(font, target, 0, 0, "A")

                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    var full = false
                    var partial = false
                    var zero = false
                    for (y in 0 until placed.size.y) {
                        for (x in 0 until placed.size.x) {
                            val coverage = chart.get(placed.source.x + x, placed.source.y + y).a
                            val dest = target.get(x, 3 + y)
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
                                    // white over opaque blue keeps the alpha, keeps blue above red
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

        test("the coverage composites over a translucent destination") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")
                val old = Pixel.rgba(255, 0, 0, 128)

                emptySprite().use { target ->
                    target.clear(old)
                    draw(font, target, 0, 0, "A")

                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    var full = false
                    var partial = false
                    var zero = false
                    for (y in 0 until placed.size.y) {
                        for (x in 0 until placed.size.x) {
                            val coverage = chart.get(placed.source.x + x, placed.source.y + y).a
                            val dest = target.get(x, 3 + y)
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

        test("a Custom mode is tapped only where the glyph has ink") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")
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
                    draw(font, target, 0, 0, "A", mode = custom)

                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    val ink = mutableListOf<Pixel>()
                    var blank = 0
                    for (y in 0 until placed.size.y) {
                        for (x in 0 until placed.size.x) {
                            val coverage = chart.get(placed.source.x + x, placed.source.y + y).a
                            if (coverage == 0) {
                                blank++
                                target.get(x, 3 + y) shouldBe Colors.TRANSPARENT
                            } else {
                                ink += Pixel.rgba(255, 255, 255, coverage)
                                target.get(x, 3 + y) shouldBe Colors.RED
                            }
                        }
                    }
                    // the tap visits exactly the ink cells, in no contract order
                    seen.size shouldBe ink.size
                    seen.groupingBy { it }.eachCount() shouldBe ink.groupingBy { it }.eachCount()
                    (blank > 0) shouldBe true
                }
            }
        }

        test("a space advances the pen without ink and empty text draws nothing") {
            Font.load(Roboto.romanFont).use { font ->
                TtfTextService.getTextSize(font, "A ", 16, 4) shouldBe Int2D(15, 19)

                emptySprite().use { blank ->
                    draw(font, blank, 0, 0, " ")
                    draw(font, blank, 0, 0, "")
                    blank.alphaSum() shouldBe 0
                }

                emptySprite().use { spaced ->
                    emptySprite().use { plain ->
                        draw(font, spaced, 0, 0, "A ")
                        draw(font, plain, 0, 0, "A")

                        spaced.pixels() shouldBe plain.pixels()
                        spaced.alphaSum() shouldBe 9983
                    }
                }
            }
        }

        test("kerning moves the second glyph to the shaped advance") {
            Font.load(Roboto.romanFont).use { font ->
                font.pensOf("AV") shouldBe
                    listOf(Float2D(0f, 14.84375f), Float2D(9.765625f, 14.84375f))

                TtfTextService.getTextSize(font, "AV", 16, 4) shouldBe Int2D(20, 19)
                TtfTextService.getTextSize(font, "AA", 16, 4) shouldBe Int2D(21, 19)
            }
        }

        test("a decomposed mark keeps its offset, advances nothing and sits above the base") {
            Font.load(Roboto.romanFont).use { font ->
                val glyphs = font.shape("x\u0301", 16).glyphs
                glyphs.size shouldBe 2
                glyphs[1].offset shouldBe Float2D(0.453125f, -0.078125f)
                glyphs[1].advance shouldBe Float2D(0f, 0f)

                // the mark's pen is where "x" alone ends, and its zero advance leaves the box unchanged
                font.pensOf("x\u0301").last().x shouldBe 7.9375f
                TtfTextService.getTextSize(font, "x\u0301", 16, 4) shouldBe Int2D(8, 19)
                TtfTextService.getTextSize(font, "x\u0301", 16, 4) shouldBe
                    TtfTextService.getTextSize(font, "x", 16, 4)

                emptySprite().use { plain ->
                    emptySprite().use { paired ->
                        draw(font, plain, 0, 0, "x")
                        draw(font, paired, 0, 0, "x\u0301")

                        // the base alone: (0, round(14.84375) - 9) = (0, 6)
                        plain.inkCells().bounds() shouldBe (Int2D(0, 6) to Int2D(7, 14))

                        val mark = font.glyph(16, 169) as AtlasGlyph.Placed
                        mark.size shouldBe Int2D(5, 2)
                        mark.bearing shouldBe Int2D(-6, -12)

                        // (round(7.9375 + 0.453125) - 6, round(14.84375 + 0.078125) - 12)
                        val markCells = paired.diffFrom(plain)
                        markCells.bounds() shouldBe (Int2D(2, 3) to Int2D(6, 4))
                        markCells.sumOf { paired.get(it.x, it.y).a } shouldBe 736
                        (markCells.maxOf { it.y } < plain.inkCells().minOf { it.y }) shouldBe true
                    }
                }
            }
        }

        test("scale doubles the painted block and the advance") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")

                emptySprite(width = 48, height = 32).use { target ->
                    draw(font, target, 0, 0, "A", scale = 2)

                    // (0, round(14.84375 * 2) - 12 * 2) = (0, 6), an 11x12 box at 2x
                    target.inkCells().bounds() shouldBe (Int2D(0, 6) to Int2D(21, 29))

                    val chart = font.atlas(16)!!.charts[placed.chartIndex]
                    for (y in 0 until placed.size.y) {
                        for (x in 0 until placed.size.x) {
                            val coverage = chart.get(placed.source.x + x, placed.source.y + y).a
                            val expected = expectedPixel(Colors.WHITE, coverage)
                            for (block in 0..1) {
                                target.get(x * 2, 6 + y * 2 + block) shouldBe expected
                                target.get(x * 2 + 1, 6 + y * 2 + block) shouldBe expected
                            }
                        }
                    }
                    // one covered pixel painted four times: 4 * 9983
                    target.alphaSum() shouldBe 39932
                }

                emptySprite(width = 48, height = 32).use { target ->
                    draw(font, target, 0, 0, "AA", scale = 2)

                    // the second A at round(10.4375 * 2) = 21, so the pair spans 0..42
                    target.inkCells().bounds() shouldBe (Int2D(0, 6) to Int2D(42, 29))
                }
            }
        }

        test("the scale anchors on the line box, not the draw origin") {
            Font.load(Roboto.romanFont).use { font ->
                emptySprite(width = 48, height = 40).use { target ->
                    draw(font, target, 2, 2, "A", scale = 2)

                    // (2 + round(0 * 2) + 0, 2 + round(14.84375 * 2) - 12 * 2) = (2, 8), 11x12 at 2x
                    target.inkCells().bounds() shouldBe (Int2D(2, 8) to Int2D(23, 31))
                    target.alphaSum() shouldBe 39932
                }
            }
        }

        test("a scaled newline advances by the scaled line height") {
            Font.load(Roboto.romanFont).use { font ->
                emptySprite(width = 32, height = 72).use { one ->
                    emptySprite(width = 32, height = 72).use { two ->
                        draw(font, one, 2, 2, "A", scale = 2)
                        draw(font, two, 2, 2, "A\nA", scale = 2)

                        // (2 + round((19 + 14.84375) * 2) - 24) = 46, one scaled line height below line 1
                        val second = two.diffFrom(one)
                        second.bounds() shouldBe (Int2D(2, 46) to Int2D(23, 69))
                        second.sumOf { two.get(it.x, it.y).a } shouldBe 39932
                    }
                }
            }
        }

        test("a non-positive scale paints nothing and skips the other checks") {
            Font.load(Roboto.romanFont).use { font ->
                emptySprite().use { target ->
                    draw(font, target, 0, 0, "A", scale = 0, tabSizeInSpaces = 0, sizePx = 0)
                    draw(font, target, 0, 0, "A", scale = -1)

                    target.alphaSum() shouldBe 0
                }
            }
        }

        test("a non-positive size or tab size fails fast") {
            Font.load(Roboto.romanFont).use { font ->
                shouldThrow<IllegalArgumentException> { TtfTextService.getTextSize(font, "A", 0, 4) }
                shouldThrow<IllegalStateException> { TtfTextService.getTextSize(font, "A", 16, 0) }

                emptySprite().use { target ->
                    shouldThrow<IllegalArgumentException> { draw(font, target, 0, 0, "A", sizePx = 0) }
                    shouldThrow<IllegalStateException> { draw(font, target, 0, 0, "A", tabSizeInSpaces = 0) }
                }
            }
        }

        test("a below mark's negated offset lands under the base") {
            Font.load(Roboto.romanFont).use { font ->
                val glyphs = font.shape("q\u0323", 16).glyphs
                glyphs.size shouldBe 2
                glyphs[1].offset shouldBe Float2D(2.734375f, -3.171875f)
                glyphs[1].advance shouldBe Float2D(0f, 0f)
                font.pensOf("q\u0323").last().x shouldBe 9.09375f

                emptySprite().use { plain ->
                    emptySprite().use { paired ->
                        draw(font, plain, 0, 0, "q")
                        draw(font, paired, 0, 0, "q\u0323")

                        val base = font.glyph(16, 85) as AtlasGlyph.Placed
                        base.size shouldBe Int2D(8, 12)
                        base.bearing shouldBe Int2D(0, -9)

                        val mark = font.glyph(16, 173) as AtlasGlyph.Placed
                        mark.size shouldBe Int2D(3, 2)
                        mark.bearing shouldBe Int2D(-6, 1)

                        // (round(9.09375 + 2.734375) - 6, round(14.84375 + 3.171875) + 1) = (6, 19)
                        val markCells = paired.diffFrom(plain)
                        markCells.bounds() shouldBe (Int2D(6, 19) to Int2D(8, 20))
                        markCells.sumOf { paired.get(it.x, it.y).a } shouldBe 518
                        // the dot's ink leaves the 19-row pen box, below the base's
                        (markCells.minOf { it.y } > plain.inkCells().maxOf { it.y }) shouldBe true
                    }
                }
            }
        }

        test("a glyph partly outside the target paints only the inside") {
            Font.load(Roboto.romanFont).use { font ->
                val placed = font.placedOf("A")
                val chart = font.atlas(16)!!.charts[placed.chartIndex]

                emptySprite().use { target ->
                    draw(font, target, -5, 0, "A")
                    target.pixels() shouldBe
                        clippedPixels(chart, placed, Int2D(-5, 3), target.width, target.height)

                    // the visible left column is atlas column 5, not 0
                    target.get(0, 3) shouldBe
                        expectedPixel(Colors.WHITE, chart.get(placed.source.x + 5, placed.source.y).a)
                }

                emptySprite().use { target ->
                    draw(font, target, 30, 0, "A")
                    target.pixels() shouldBe
                        clippedPixels(chart, placed, Int2D(30, 3), target.width, target.height)
                }

                emptySprite().use { target ->
                    draw(font, target, 0, 20, "A")
                    target.pixels() shouldBe
                        clippedPixels(chart, placed, Int2D(0, 23), target.width, target.height)
                }

                emptySprite().use { target ->
                    draw(font, target, 100, 100, "A")
                    draw(font, target, -20, -20, "A")
                    target.alphaSum() shouldBe 0
                }
            }
        }

        test("a newline draws the next line one line height lower") {
            Font.load(Roboto.romanFont).use { font ->
                emptySprite(height = 40).use { one ->
                    emptySprite(height = 40).use { two ->
                        draw(font, one, 0, 0, "A")
                        draw(font, two, 0, 0, "A\nA")

                        // line 1 baseline 19 + 14.84375 rounds to 34, bearing -12 -> y 22
                        val second = two.diffFrom(one)
                        second.bounds() shouldBe (Int2D(0, 22) to Int2D(10, 33))
                        second.sumOf { two.get(it.x, it.y).a } shouldBe 9983
                    }
                }
            }
        }

        test("the Int2D draw overload lands the same pixels as the raw form") {
            Font.load(Roboto.romanFont).use { font ->
                emptySprite(width = 48, height = 32).use { raw ->
                    emptySprite(width = 48, height = 32).use { positioned ->
                        draw(font, raw, 3, 4, "A")
                        TtfTextService.drawString(
                            font,
                            positioned,
                            Int2D(3, 4),
                            "A",
                            16,
                            Colors.WHITE,
                            1,
                            4,
                            Pixel.Mode.Normal,
                        )

                        raw.alphaSum() shouldBe 9983
                        positioned.pixels() shouldBe raw.pixels()
                    }
                }
            }
        }

        test("drawing the same text twice adds no chart and reuses the cache") {
            Font.load(Roboto.romanFont).use { font ->
                val shaped = font.shape("A", 16).glyphs.single()
                val glyphId = shaped.glyphId

                emptySprite().use { first ->
                    emptySprite().use { second ->
                        draw(font, first, 0, 0, "AV")
                        val charts = font.atlas(16)!!.charts
                        val entry = font.glyph(16, glyphId)
                        charts.size shouldBe 1

                        draw(font, second, 0, 0, "AV")
                        val after = font.atlas(16)!!.charts

                        after.size shouldBe charts.size
                        after[0] shouldBeSameInstanceAs charts[0]
                        font.glyph(16, glyphId) shouldBeSameInstanceAs entry
                        second.pixels() shouldBe first.pixels()
                    }
                }
            }
        }

        test("drawing after the font is closed fails fast") {
            val font = Font.load(Roboto.romanFont)
            font.close()

            shouldThrow<IllegalStateException> { TtfTextService.getTextSize(font, "A", 16, 4) }
            emptySprite().use { target ->
                shouldThrow<IllegalStateException> { draw(font, target, 0, 0, "A") }
            }
        }
    })

/** The float pens the walk reports for [text], drawn from `(0, 0)` at 16 px. */
private fun Font.pensOf(
    text: String,
    sizePx: Int = 16,
    tabSizeInSpaces: Int = 4,
): List<Float2D> {
    val pens = mutableListOf<Float2D>()
    walkText(text, sizePx, tabSizeInSpaces, 0, 0) { _, penX, penY -> pens += Float2D(penX, penY) }
    return pens
}

/** The atlas entry of the single glyph [glyph] is shaped from. */
private fun Font.placedOf(glyph: String): AtlasGlyph.Placed {
    val shaped = shape(glyph, 16).glyphs.single()
    return glyph(16, shaped.glyphId) as AtlasGlyph.Placed
}

/** Draws [text] at 16 px with the service, the module's public entry point. */
private fun draw(
    font: Font,
    target: Pixmap.Mutable,
    x: Int,
    y: Int,
    text: String,
    color: Pixel = Colors.WHITE,
    scale: Int = 1,
    tabSizeInSpaces: Int = 4,
    mode: Pixel.Mode = Pixel.Mode.Normal,
    sizePx: Int = 16,
) = TtfTextService.drawString(font, target, x, y, text, sizePx, color, scale, tabSizeInSpaces, mode)

/** The pixel a coverage byte [coverage] composites to under an opaque [tint]. */
private fun expectedPixel(
    tint: Pixel,
    coverage: Int,
): Pixel {
    val alpha = tint.a * coverage / 255
    return if (alpha == 0) Colors.TRANSPARENT else Pixel.rgba(tint.r, tint.g, tint.b, alpha)
}

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

/** The whole surface [placed] paints at [dest], clipped to [width]x[height]. */
private fun clippedPixels(
    chart: Pixmap,
    placed: AtlasGlyph.Placed,
    dest: Int2D,
    width: Int,
    height: Int,
): List<Pixel> {
    val pixels = mutableListOf<Pixel>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val inside =
                x >= dest.x &&
                    y >= dest.y &&
                    x < dest.x + placed.size.x &&
                    y < dest.y + placed.size.y
            pixels +=
                if (inside) {
                    expectedPixel(Colors.WHITE, chart.get(placed.source.x + x - dest.x, placed.source.y + y - dest.y).a)
                } else {
                    Colors.TRANSPARENT
                }
        }
    }
    return pixels
}

private fun emptySprite(
    width: Int = 32,
    height: Int = 24,
): Sprite =
    SpriteService
        .create(width, height, Pixmap.SampleMode.NORMAL, "text draw test")
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
