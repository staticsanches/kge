package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * The atlas is pure CPU logic: a stub rasterizer pins shelf placement, the
 * white-alpha encoding, the blank and cache behavior and the resource
 * contract, with no native stack involved.
 */
class GlyphAtlasTest :
    FunSpec({
        test("a fixed glyph sequence packs into deterministic shelf rows") {
            val raster =
                StubRaster(
                    mapOf(
                        1 to coverage(4, 3, Int2D(1, -3), 3),
                        2 to coverage(6, 2, Int2D(0, -2), 5),
                        3 to coverage(4, 3, Int2D(2, -3), 7),
                        4 to coverage(5, 5, Int2D(3, -5), 11),
                        5 to coverage(2, 2, Int2D(0, -2), 13),
                    ),
                )

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                atlas.glyph(1) shouldBe AtlasGlyph.Placed(0, Int2D(0, 0), Int2D(4, 3), Int2D(1, -3))
                atlas.glyph(2) shouldBe AtlasGlyph.Placed(0, Int2D(4, 0), Int2D(6, 2), Int2D(0, -2))
                atlas.glyph(3) shouldBe AtlasGlyph.Placed(0, Int2D(10, 0), Int2D(4, 3), Int2D(2, -3))
                atlas.glyph(4) shouldBe AtlasGlyph.Placed(0, Int2D(0, 3), Int2D(5, 5), Int2D(3, -5))
                atlas.glyph(5) shouldBe AtlasGlyph.Placed(0, Int2D(14, 0), Int2D(2, 2), Int2D(0, -2))

                atlas.charts.size shouldBe 1
                atlas.charts.single().name shouldBe "glyph atlas (16px) #0"
            }
        }

        test("a glyph that does not fit the first chart opens a second") {
            val raster =
                StubRaster(
                    mapOf(
                        1 to coverage(512, 512, Int2D(0, -512), 1),
                        2 to coverage(512, 512, Int2D(0, -512), 2),
                    ),
                )

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                atlas.glyph(1) shouldBe AtlasGlyph.Placed(0, Int2D(0, 0), Int2D(512, 512), Int2D(0, -512))
                atlas.glyph(2) shouldBe AtlasGlyph.Placed(1, Int2D(0, 0), Int2D(512, 512), Int2D(0, -512))

                atlas.charts.map { it.name } shouldBe
                    listOf("glyph atlas (16px) #0", "glyph atlas (16px) #1")
            }
        }

        test("a placed glyph carries opaque white with the coverage as alpha") {
            val raster =
                StubRaster(
                    mapOf(1 to GlyphCoverage(2, 2, Int2D(1, -2), byteArrayOf(0, 0xFF.toByte(), 128.toByte(), 1))),
                )

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                atlas.glyph(1) shouldBe AtlasGlyph.Placed(0, Int2D(0, 0), Int2D(2, 2), Int2D(1, -2))
                val chart = atlas.charts.single()

                chart.get(0, 0) shouldBe Colors.TRANSPARENT
                chart.get(1, 0) shouldBe Colors.WHITE
                chart.get(0, 1) shouldBe Pixel.rgba(255, 255, 255, 128)
                chart.get(1, 1) shouldBe Pixel.rgba(255, 255, 255, 1)
            }
        }

        test("the placed box alpha reproduces the coverage bytes in row-major order") {
            val ink = coverage(4, 3, Int2D(2, -3), 17)
            val raster = StubRaster(mapOf(1 to ink))

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                val placed = atlas.glyph(1) as AtlasGlyph.Placed
                val chart = atlas.charts.single()

                val alphas =
                    (0 until placed.size.y).flatMap { y ->
                        (0 until placed.size.x).map { x ->
                            chart.get(placed.source.x + x, placed.source.y + y).a
                        }
                    }

                alphas shouldBe ink.coverage.map { it.toInt() and 0xFF }
            }
        }

        test("the total alpha written equals the total coverage") {
            val firstInk = coverage(4, 3, Int2D(1, -3), 3)
            val secondInk = coverage(6, 2, Int2D(0, -2), 5)
            val raster = StubRaster(mapOf(1 to firstInk, 2 to secondInk))

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                val first = atlas.glyph(1) as AtlasGlyph.Placed
                val second = atlas.glyph(2) as AtlasGlyph.Placed
                val chart = atlas.charts.single()

                fun alphaIn(placed: AtlasGlyph.Placed): Int =
                    (0 until placed.size.y).sumOf { y ->
                        (0 until placed.size.x).sumOf { x ->
                            chart.get(placed.source.x + x, placed.source.y + y).a
                        }
                    }

                alphaIn(first) + alphaIn(second) shouldBe
                    firstInk.coverage.sumOf { it.toInt() and 0xFF } +
                    secondInk.coverage.sumOf { it.toInt() and 0xFF }
            }
        }

        test("a blank glyph is Blank and never grows a chart") {
            val raster = StubRaster(mapOf(32 to GlyphCoverage(0, 0, Int2D(0, 0), ByteArray(0))))

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                atlas.glyph(32) shouldBe AtlasGlyph.Blank
                atlas.glyph(32) shouldBe AtlasGlyph.Blank

                atlas.charts shouldBe emptyList()
                raster.calls shouldBe listOf(32)
            }
        }

        test("a repeated glyph is rasterized once and reuses its cached entry") {
            val raster = StubRaster(mapOf(1 to coverage(4, 3, Int2D(1, -3), 3)))

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                val first = atlas.glyph(1)
                val second = atlas.glyph(1)

                second shouldBeSameInstanceAs first
                raster.calls shouldBe listOf(1)
                atlas.charts.size shouldBe 1
            }
        }

        test("a coverage wider or taller than a chart fails fast") {
            val raster =
                StubRaster(
                    mapOf(
                        1 to coverage(513, 1),
                        2 to coverage(1, 513),
                    ),
                )

            GlyphAtlas(16, raster::rasterize).use { atlas ->
                val wide = shouldThrow<IllegalArgumentException> { atlas.glyph(1) }
                wide.message shouldContain "513x1"
                wide.message shouldContain "512"

                val tall = shouldThrow<IllegalArgumentException> { atlas.glyph(2) }
                tall.message shouldContain "1x513"

                atlas.charts shouldBe emptyList()
            }
        }

        test("close is idempotent and use after close fails fast") {
            val raster = StubRaster(mapOf(1 to coverage(4, 3, Int2D(1, -3), 3)))
            val atlas = GlyphAtlas(16, raster::rasterize)
            atlas.glyph(1)
            val chart = atlas.charts.single()

            atlas.close()
            atlas.close()

            shouldThrow<IllegalStateException> { chart.get(0, 0) }
            val failure = shouldThrow<IllegalStateException> { atlas.glyph(1) }
            failure.message shouldContain "released"
        }
    })

/** A rasterizer stub: it records the glyph ids it was asked to render. */
private class StubRaster(
    private val coverages: Map<Int, GlyphCoverage>,
) {
    val calls = mutableListOf<Int>()

    fun rasterize(glyphId: Int): GlyphCoverage {
        calls += glyphId
        return coverages.getValue(glyphId)
    }
}

/** Deterministic ink: a spread of alpha values, zeros and 255s included. */
private fun coverage(
    width: Int,
    height: Int,
    bearing: Int2D = Int2D(0, 0),
    seed: Int = 0,
): GlyphCoverage =
    GlyphCoverage(
        width = width,
        height = height,
        bearing = bearing,
        coverage = ByteArray(width * height) { index -> ((index * 7 + seed) % 256).toByte() },
    )
