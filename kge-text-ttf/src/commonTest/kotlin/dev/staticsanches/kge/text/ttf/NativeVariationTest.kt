package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * One payload handed to several native faces at once, each at its own
 * coordinates: the advance pins HarfBuzz and the coverage sum pins FreeType.
 */
class NativeVariationTest :
    FunSpec({
        test("the default instance keeps the pinned advance and ink") {
            // the fixture identity, so a font bump fails here instead of re-pinning
            Roboto.FAMILY shouldBe "Roboto"
            Roboto.VERSION shouldBe "3.015"

            withPayload { payload ->
                val face = createNativeFace(payload, AxisCoordinates.Empty)
                try {
                    val shaped = face.shape(CODE_POINT_A, SIZE_PX).single()
                    shaped.advance.x shouldBe 10.4375f

                    val raster = face.rasterize(shaped.glyphId, SIZE_PX)
                    // round D: "A" at 16 px is 11x12 at bearing (0, -12)
                    raster.width shouldBe 11
                    raster.height shouldBe 12
                    raster.bearing shouldBe Int2D(0, -12)
                    raster.coverageSum shouldBe 9983
                } finally {
                    closeNativeFace(face)
                }
            }
        }

        test("wght 900 moves the shaped advance and the raster ink together") {
            withPayload { payload ->
                val face = createNativeFace(payload, WEIGHT_900_COORDINATES)
                try {
                    val shaped = face.shape(CODE_POINT_A, SIZE_PX).single()
                    // the axis measurements: the advance is 698/64
                    shaped.advance.x shouldBe 10.90625f

                    val raster = face.rasterize(shaped.glyphId, SIZE_PX)
                    // the 900 instance is 12x12 at bearing (-1, -12)
                    raster.width shouldBe 12
                    raster.height shouldBe 12
                    raster.bearing shouldBe Int2D(-1, -12)
                    // heavier ink, not only a bigger box
                    raster.coverageSum shouldBe 16978
                } finally {
                    closeNativeFace(face)
                }
            }
        }

        test("closing one face leaves another over the same payload usable") {
            withPayload { payload ->
                val default = createNativeFace(payload, AxisCoordinates.Empty)
                val bold = createNativeFace(payload, WEIGHT_900_COORDINATES)
                try {
                    closeNativeFace(default)

                    val shaped = bold.shape(CODE_POINT_A, SIZE_PX).single()
                    shaped.advance.x shouldBe 10.90625f
                    bold.rasterize(shaped.glyphId, SIZE_PX).coverageSum shouldBe 16978
                } finally {
                    closeNativeFace(bold)
                }
            }
        }

        test("the payload closes after both faces and the close is idempotent") {
            val payload = TtfPayload(robotoFontBytes())
            try {
                closeNativeFace(createNativeFace(payload, AxisCoordinates.Empty))
                closeNativeFace(createNativeFace(payload, WEIGHT_900_COORDINATES))
            } finally {
                payload.close()
            }
            payload.close()
        }
    })

private const val SIZE_PX = 16

/** `wght` packed big-endian. */
private const val WEIGHT_TAG = 0x77676874

/** The raw 16.16 design value of the pinned heavy instance. */
private const val WEIGHT_900 = 900 * 65536

/** `shape` takes code points, so the single "A" of the measurements. */
private val CODE_POINT_A = intArrayOf('A'.code)

private val WEIGHT_900_COORDINATES = AxisCoordinates(intArrayOf(WEIGHT_TAG), intArrayOf(WEIGHT_900))

private val GlyphCoverage.coverageSum: Int
    get() = coverage.sumOf { it.toInt() and 0xFF }

/** Builds the payload every face of the measurements shares, and closes it after [block]. */
private suspend fun withPayload(block: suspend (TtfPayload) -> Unit) {
    val payload = TtfPayload(robotoFontBytes())
    try {
        block(payload)
    } finally {
        payload.close()
    }
}
