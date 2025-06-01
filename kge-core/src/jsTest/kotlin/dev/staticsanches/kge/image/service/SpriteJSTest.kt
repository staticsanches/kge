package dev.staticsanches.kge.image.service

import dev.staticsanches.kge.image.Colors.BLANK
import dev.staticsanches.kge.image.Colors.BLUE
import dev.staticsanches.kge.image.Colors.LIME
import dev.staticsanches.kge.image.Colors.ORANGE
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.Colors.YELLOW
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.image.extension.loadPNG
import dev.staticsanches.kge.image.extension.loadPNGFromBase64
import dev.staticsanches.kge.image.extension.toBase64PNG
import dev.staticsanches.kge.resource.applyClosingIfFailed
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class SpriteJSTest {
    @Test
    @Ignore // TODO: Configure Karma to allow fetch of resources
    fun shouldLoadFromResource() =
        runTest {
            Sprite.loadPNG("xmas_5x5.png").await().use { sprite ->
                assertEquals(xmas5x5Pixels, sprite.toList())
            }
        }

    @Test
    fun shouldLoadFromBase64() =
        runTest {
            Sprite.loadPNGFromBase64(XMAS_5_X_5_BASE64).await().use { sprite ->
                assertEquals(xmas5x5Pixels, sprite.toList())
            }
        }

    @Test
    fun shouldWriteToBase64() =
        runTest {
            createXmas5x5().use { sprite ->
                assertEquals(xmas5x5Pixels, sprite.toList())

                val base64 = sprite.toBase64PNG()
                assertEquals(XMAS_5_X_5_BASE64, base64)

                Sprite.loadPNGFromBase64(base64).await().use { copy ->
                    assertEquals(xmas5x5Pixels, copy.toList())

                    copy.clear(BLANK)

                    assertEquals(setOf(BLANK), copy.toSet())
                }

                assertEquals(xmas5x5Pixels, sprite.toList())
            }
        }

    private fun createXmas5x5(): Sprite = Sprite.create(5, 5).applyClosingIfFailed { clear(xmas5x5Pixels) }

    companion object {
        private val xmas5x5Pixels =
            listOf(
                BLUE, BLUE, YELLOW, BLUE, BLUE,
                BLUE, RED, LIME, LIME, BLUE,
                BLUE, LIME, LIME, RED, BLUE,
                LIME, RED, LIME, LIME, RED,
                BLANK, BLANK, ORANGE, BLANK, BLANK,
            )

        @Suppress("SpellCheckingInspection")
        private const val XMAS_5_X_5_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAANUlEQVR4AT3BgQ2AMAwEsfuyeDIMc17TCmEHlKEhkWMxJIRhOBYjch" +
                "muB7sMdEEX2M3PF/ls43ASrtWnyPQAAAAASUVORK5CYII="
    }
}
