package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Colors.BLANK
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.Colors.WHITE
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.rasterizer.Rasterizer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity tests for [Rasterizer.fillRect] and [Rasterizer.drawSpan]: they assert the observable
 * behavior of the software rasterizer so the span fast-path can be optimized without changing it.
 */
class FillRectServiceTest {
    @Test
    fun shouldFillExactRectangleInNormalMode() =
        Sprite.create(10, 10, color = BLANK).use { sprite ->
            Rasterizer.fillRect(2, 3, 6, 7, RED, sprite, Pixel.Mode.Normal)

            for (y in 0..<10) {
                for (x in 0..<10) {
                    val expected = if (x in 2..6 && y in 3..7) RED else BLANK
                    assertEquals(expected, sprite[x, y], "pixel at ($x, $y)")
                }
            }
        }

    @Test
    fun shouldSupportInvertedDiagonal() =
        Sprite.create(10, 10, color = BLANK).use { sprite ->
            Rasterizer.fillRect(6, 7, 2, 3, RED, sprite, Pixel.Mode.Normal)

            for (y in 0..<10) {
                for (x in 0..<10) {
                    val expected = if (x in 2..6 && y in 3..7) RED else BLANK
                    assertEquals(expected, sprite[x, y], "pixel at ($x, $y)")
                }
            }
        }

    @Test
    fun shouldClipToTargetBounds() =
        Sprite.create(10, 10, color = BLANK).use { sprite ->
            Rasterizer.fillRect(8, 8, 100, 100, RED, sprite, Pixel.Mode.Normal)

            for (y in 0..<10) {
                for (x in 0..<10) {
                    val expected = if (x in 8..9 && y in 8..9) RED else BLANK
                    assertEquals(expected, sprite[x, y], "pixel at ($x, $y)")
                }
            }
        }

    @Test
    fun shouldIgnoreRectFullyOutsideTarget() =
        Sprite.create(4, 4, color = BLANK).use { sprite ->
            Rasterizer.fillRect(-5, -5, -1, -1, RED, sprite, Pixel.Mode.Normal)
            Rasterizer.fillRect(10, 10, 20, 20, RED, sprite, Pixel.Mode.Normal)
            Rasterizer.fillRect(0, -3, 3, -1, RED, sprite, Pixel.Mode.Normal)

            assertEquals(setOf(BLANK), sprite.toSet())
        }

    @Test
    fun shouldDrawMaskedOpaquePixelsOnly() =
        Sprite.create(4, 4, color = BLANK).use { sprite ->
            val transparentRed = Pixel.rgba(255, 0, 0, 128)
            Rasterizer.fillRect(0, 0, 1, 1, transparentRed, sprite, Pixel.Mode.Mask)
            Rasterizer.fillRect(2, 2, 3, 3, RED, sprite, Pixel.Mode.Mask)

            for (y in 0..<4) {
                for (x in 0..<4) {
                    val expected = if (x in 2..3 && y in 2..3) RED else BLANK
                    assertEquals(expected, sprite[x, y], "pixel at ($x, $y)")
                }
            }
        }

    @Test
    fun shouldBlendInAlphaMode() =
        Sprite.create(3, 3, color = WHITE).use { sprite ->
            Rasterizer.fillRect(0, 0, 1, 1, RED, sprite, Pixel.Mode.Alpha(0.5f))

            assertEquals(Pixel.rgba(255, 127, 127), sprite[0, 0])
            assertEquals(Pixel.rgba(255, 127, 127), sprite[1, 1])
            assertEquals(WHITE, sprite[2, 0])
        }

    @Test
    fun drawSpanShouldBeSafeOutsideBounds() =
        Sprite.create(4, 4, color = BLANK).use { sprite ->
            Rasterizer.drawSpan(2, 10, 1, RED, sprite, Pixel.Mode.Normal)
            Rasterizer.drawSpan(0, 3, 10, RED, sprite, Pixel.Mode.Normal)
            Rasterizer.drawSpan(5, 2, 2, RED, sprite, Pixel.Mode.Normal)

            for (y in 0..<4) {
                for (x in 0..<4) {
                    val expected = if (x in 2..3 && y == 1) RED else BLANK
                    assertEquals(expected, sprite[x, y], "pixel at ($x, $y)")
                }
            }
        }

    @Test
    fun drawSpanShouldFallbackForNonNormalModes() =
        Sprite.create(4, 4, color = BLANK).use { sprite ->
            Rasterizer.drawSpan(1, 2, 1, Pixel.rgba(255, 0, 0, 128), sprite, Pixel.Mode.Mask)

            assertEquals(setOf(BLANK), sprite.toSet())
        }
}
