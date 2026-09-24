package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The raster contract of the seam, pinned on the shipped Roboto fixture at its
 * default instance: the bitmap box, the pen bearing and the total coverage,
 * identical on JVM and both web targets.
 */
@OptIn(KGESensitiveAPI::class)
class GlyphRasterTest :
    FunSpec({
        test("the pinned 16 px raster boxes") {
            // the fixture identity, so a font bump fails here instead of re-pinning
            Roboto.FAMILY shouldBe "Roboto"
            Roboto.VERSION shouldBe "3.015"

            withRobotoFace { face ->
                RASTER_GLYPHS.forEachIndexed { index, glyph ->
                    withClue(glyph) {
                        val raster = face.raster(glyph, 16)
                        raster.width shouldBe PINNED_16[index].width
                        raster.height shouldBe PINNED_16[index].height
                        raster.coverage.size shouldBe PINNED_16[index].width * PINNED_16[index].height
                    }
                }
            }
        }

        test("the pinned 16 px pen bearings") {
            withRobotoFace { face ->
                RASTER_GLYPHS.forEachIndexed { index, glyph ->
                    withClue(glyph) { face.raster(glyph, 16).bearing shouldBe PINNED_16[index].bearing }
                }
            }
        }

        test("the pinned 16 px coverage totals") {
            withRobotoFace { face ->
                RASTER_GLYPHS.forEachIndexed { index, glyph ->
                    withClue(glyph) { face.raster(glyph, 16).coverageAlpha shouldBe PINNED_16[index].coverage }
                }
            }
        }

        test("32 px rasterizes a larger box and coverage without clipping") {
            withRobotoFace { face ->
                RASTER_GLYPHS.forEachIndexed { index, glyph ->
                    withClue(glyph) {
                        val small = face.raster(glyph, 16)
                        val large = face.raster(glyph, 32)
                        large.width shouldBe PINNED_32[index].width
                        large.height shouldBe PINNED_32[index].height
                        large.bearing shouldBe PINNED_32[index].bearing
                        large.coverageAlpha shouldBe PINNED_32[index].coverage
                        large.coverage.size shouldBe PINNED_32[index].width * PINNED_32[index].height
                        (large.width > small.width) shouldBe true
                        (large.height > small.height) shouldBe true
                        (large.coverageAlpha > small.coverageAlpha) shouldBe true
                    }
                }
            }
        }

        test("a blank glyph carries no coverage") {
            withRobotoFace { face ->
                val raster = face.raster(" ", 16)

                raster.width shouldBe 0
                raster.height shouldBe 0
                raster.bearing shouldBe Int2D(0, 0)
                raster.coverage shouldBe ByteArray(0)
            }
        }
    })

/** The fixture glyphs the raster table is pinned on, in table order. */
private val RASTER_GLYPHS = listOf("A", "V", "o", "g", "1", "\u00e9")

/** The Roboto 3.015 default instance at 16 px: box, pen bearing and Σ coverage bytes. */
private val PINNED_16 =
    listOf(
        PinnedRaster(11, 12, Int2D(0, -12), 9983),
        PinnedRaster(10, 12, Int2D(0, -12), 8878),
        PinnedRaster(9, 9, Int2D(0, -9), 7634),
        PinnedRaster(8, 12, Int2D(0, -9), 11256),
        PinnedRaster(5, 12, Int2D(1, -12), 5418),
        PinnedRaster(8, 12, Int2D(0, -12), 8765),
    )

/** The same glyphs at 32 px: a strictly larger box and coverage, nothing clipped. */
private val PINNED_32 =
    listOf(
        PinnedRaster(21, 23, Int2D(0, -23), 38047),
        PinnedRaster(20, 23, Int2D(0, -23), 33689),
        PinnedRaster(16, 17, Int2D(1, -17), 29348),
        PinnedRaster(15, 24, Int2D(1, -17), 42512),
        PinnedRaster(10, 23, Int2D(2, -23), 20645),
        PinnedRaster(15, 25, Int2D(1, -25), 34659),
    )

private data class PinnedRaster(
    val width: Int,
    val height: Int,
    val bearing: Int2D,
    val coverage: Int,
)

private val GlyphCoverage.coverageAlpha: Int
    get() = coverage.sumOf { it.toInt() and 0xFF }

/** The seam owns the payload; the face releases it, and a failed open releases it here. */
@OptIn(KGESensitiveAPI::class)
private suspend fun <T> withRobotoFace(block: (NativeFace) -> T): T {
    val bytes = robotoFontBytes()
    val storage = BufferService.allocate(bytes.size, "raster test")
    try {
        val buffer = storage.resource
        for (index in bytes.indices) {
            buffer.put(index, bytes[index])
        }
        val face = createNativeFace(storage)
        try {
            return block(face)
        } finally {
            closeNativeFace(face)
        }
    } catch (failure: Throwable) {
        try {
            storage.close()
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw failure
    }
}

private fun NativeFace.raster(
    glyph: String,
    sizePx: Int,
): GlyphCoverage {
    val glyphId = shape(intArrayOf(glyph[0].code), sizePx).single().glyphId
    return rasterize(glyphId, sizePx)
}
