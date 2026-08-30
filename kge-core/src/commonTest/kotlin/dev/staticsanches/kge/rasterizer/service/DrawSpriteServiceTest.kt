package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Colors.BLANK
import dev.staticsanches.kge.image.Colors.BLUE
import dev.staticsanches.kge.image.Colors.GREEN
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.Colors.WHITE
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.resource.applyClosingIfFailed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity tests for [Rasterizer.drawSprite]: they assert the observable behavior of the software
 * rasterizer so the sprite fast-paths can be optimized without changing it.
 */
class DrawSpriteServiceTest {
    private val sourcePixels =
        listOf(
            RED, GREEN, BLUE,
            GREEN, BLUE, RED,
            BLUE, RED, GREEN,
        )

    private fun createSource(): Sprite = Sprite.create(3, 3, color = BLANK).applyClosingIfFailed { clear(sourcePixels) }

    @Test
    fun shouldDrawExactSpriteInNormalMode() =
        createSource().use { source ->
            Sprite.create(5, 5, color = BLANK).use { target ->
                Rasterizer.drawSprite(1, 1, source, 1, Sprite.Flip.NONE, target, Pixel.Mode.Normal)

                for (y in 0..<5) {
                    for (x in 0..<5) {
                        val expected = if (x in 1..3 && y in 1..3) sourcePixels[(y - 1) * 3 + (x - 1)] else BLANK
                        assertEquals(expected, target[x, y], "pixel at ($x, $y)")
                    }
                }
            }
        }

    @Test
    fun shouldDrawScaledSpriteInNormalMode() =
        Sprite.create(2, 2, color = BLANK)
            .applyClosingIfFailed { clear(listOf(RED, GREEN, BLUE, RED)) }
            .use { source ->
                Sprite.create(4, 4, color = BLANK).use { target ->
                    Rasterizer.drawSprite(0, 0, source, 2, Sprite.Flip.NONE, target, Pixel.Mode.Normal)

                    val expected =
                        listOf(
                            RED, RED, GREEN, GREEN,
                            RED, RED, GREEN, GREEN,
                            BLUE, BLUE, RED, RED,
                            BLUE, BLUE, RED, RED,
                        )

                    assertEquals(expected, target.toList())
                }
            }

    @Test
    fun shouldSupportHorizontalFlip() =
        createSource().use { source ->
            Sprite.create(5, 5, color = BLANK).use { target ->
                Rasterizer.drawSprite(0, 0, source, 1, Sprite.Flip.HORIZONTAL, target, Pixel.Mode.Normal)

                for (y in 0..<3) {
                    for (x in 0..<3) {
                        val expected = sourcePixels[y * 3 + (2 - x)]
                        assertEquals(expected, target[x, y], "pixel at ($x, $y)")
                    }
                }
            }
        }

    @Test
    fun shouldSupportVerticalFlip() =
        createSource().use { source ->
            Sprite.create(5, 5, color = BLANK).use { target ->
                Rasterizer.drawSprite(0, 0, source, 1, Sprite.Flip.VERTICAL, target, Pixel.Mode.Normal)

                for (y in 0..<3) {
                    for (x in 0..<3) {
                        val expected = sourcePixels[(2 - y) * 3 + x]
                        assertEquals(expected, target[x, y], "pixel at ($x, $y)")
                    }
                }
            }
        }

    @Test
    fun shouldClipWhenPartiallyOutsideTarget() =
        createSource().use { source ->
            Sprite.create(5, 5, color = BLANK).use { target ->
                Rasterizer.drawSprite(-1, -1, source, 1, Sprite.Flip.NONE, target, Pixel.Mode.Normal)

                for (y in 0..<5) {
                    for (x in 0..<5) {
                        val expected = if (x in 0..1 && y in 0..1) sourcePixels[(y + 1) * 3 + (x + 1)] else BLANK
                        assertEquals(expected, target[x, y], "pixel at ($x, $y)")
                    }
                }
            }
        }

    @Test
    fun shouldDrawPartialSprite() =
        createSource().use { source ->
            Sprite.create(5, 5, color = BLANK).use { target ->
                Rasterizer.drawPartialSprite(0, 0, source, 1, 1, 2, 2, 1, Sprite.Flip.NONE, target, Pixel.Mode.Normal)

                val expected = sourcePixels[(1) * 3 + 1]
                assertEquals(expected, target[0, 0])
                assertEquals(sourcePixels[1 * 3 + 2], target[1, 0])
                assertEquals(sourcePixels[2 * 3 + 1], target[0, 1])
                assertEquals(sourcePixels[2 * 3 + 2], target[1, 1])
                assertEquals(BLANK, target[2, 0])
            }
        }

    @Test
    fun shouldBlendInAlphaMode() =
        Sprite.create(2, 2, color = BLANK)
            .applyClosingIfFailed {
                clear(
                    listOf(
                        Pixel.rgba(255, 0, 0),
                        Pixel.rgba(0, 255, 0),
                        Pixel.rgba(0, 0, 255),
                        Pixel.rgba(255, 255, 255),
                    ),
                )
            }
            .use { source ->
                Sprite.create(4, 4, color = WHITE).use { target ->
                    Rasterizer.drawSprite(0, 0, source, 1, Sprite.Flip.NONE, target, Pixel.Mode.Alpha(0.5f))

                    // X over WHITE at 50% -> (127 + X/2, ...)
                    assertEquals(Pixel.rgba(255, 127, 127), target[0, 0])
                    assertEquals(Pixel.rgba(127, 255, 127), target[1, 0])
                    assertEquals(Pixel.rgba(127, 127, 255), target[0, 1])
                    assertEquals(WHITE, target[1, 1]) // white over white at 50% is still white
                    assertEquals(WHITE, target[2, 0])
                }
            }

    @Test
    fun shouldDrawMaskedOpaquePixels() =
        createSource().use { source ->
            Sprite.create(5, 5, color = BLANK).use { target ->
                Rasterizer.drawSprite(0, 0, source, 1, Sprite.Flip.NONE, target, Pixel.Mode.Mask)

                for (y in 0..<3) {
                    for (x in 0..<3) {
                        assertEquals(sourcePixels[y * 3 + x], target[x, y], "pixel at ($x, $y)")
                    }
                }
            }
        }
}
