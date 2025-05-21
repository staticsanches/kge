package dev.staticsanches.kge.image

import dev.staticsanches.kge.image.Colors.LIGHT_BLUE
import dev.staticsanches.kge.image.Colors.LIGHT_CORAL
import dev.staticsanches.kge.image.Colors.OLIVE_DRAB
import dev.staticsanches.kge.image.Colors.PALE_VIOLET_RED
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.Colors.SIENNA
import dev.staticsanches.kge.image.Colors.STEEL_BLUE
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.image.extension.duplicate
import kotlin.sequences.toList
import kotlin.test.Test
import kotlin.test.assertEquals

class SpriteTest {
    @Test
    fun shouldDuplicateSuccessfully(): Unit =
        Sprite.create(2, 3).use { original ->
            assertEquals(2, original.width)
            assertEquals(3, original.height)
            assertEquals(setOf(Colors.BLANK), original.toSet())

            original[0, 0] = LIGHT_BLUE
            original[0, 1] = LIGHT_CORAL
            original[0, 2] = SIENNA
            original[1, 0] = STEEL_BLUE
            original[1, 1] = PALE_VIOLET_RED
            original[1, 2] = OLIVE_DRAB

            val expectedPixels =
                listOf(
                    LIGHT_BLUE, STEEL_BLUE,
                    LIGHT_CORAL, PALE_VIOLET_RED,
                    SIENNA, OLIVE_DRAB,
                )

            assertEquals(expectedPixels, original.toList())

            original.duplicate().use { copy ->
                assertEquals(expectedPixels, copy.toList())

                copy.clear(RED)

                assertEquals(setOf(RED), copy.toSet())

                copy.clear(expectedPixels)

                assertEquals(expectedPixels, copy.toList())
            }

            assertEquals(expectedPixels, original.toList())
        }
}
