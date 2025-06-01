package dev.staticsanches.kge.image

import dev.staticsanches.kge.image.Colors.BLANK
import dev.staticsanches.kge.image.Colors.BLUE
import dev.staticsanches.kge.image.Colors.LIME
import dev.staticsanches.kge.image.Colors.ORANGE
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.Colors.YELLOW
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.image.extension.loadPNG
import dev.staticsanches.kge.image.extension.loadPNGFromBase64
import dev.staticsanches.kge.image.extension.toBase64PNG
import dev.staticsanches.kge.image.extension.writePNG
import dev.staticsanches.kge.resource.applyClosingIfFailed
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

class SpriteJVMTest {
    @Test
    fun shouldLoadFromFile(): Unit =
        Sprite.loadPNG(Paths.get(xmas5x5URL.toURI()).absolutePathString()).use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())
        }

    @Test
    fun shouldLoadFromURL(): Unit =
        Sprite.loadPNG(xmas5x5URL).use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())
        }

    @Test
    fun shouldLoadFromInputStream(): Unit =
        Sprite.loadPNG(xmas5x5URL::openStream).use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())
        }

    @Test
    fun shouldLoadFromBase64(): Unit =
        Sprite.loadPNGFromBase64(XMAS_5_X_5_BASE64).use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())
        }

    @Test
    fun shouldWriteToFile(): Unit =
        createXmas5x5().use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())

            val file = Files.createTempFile("xmas5x5", ".png").toFile()
            file.deleteOnExit()

            sprite.writePNG(file.absolutePath)
            assertEquals(xmas5x5Pixels, sprite.toList())

            Sprite.loadPNG(file.absolutePath).use { copy ->
                assertEquals(xmas5x5Pixels, copy.toList())

                copy.clear(BLANK)

                assertEquals(setOf(BLANK), copy.toSet())
            }

            assertEquals(xmas5x5Pixels, sprite.toList())

            file.delete()
        }

    @Test
    fun shouldWriteToOutputStream(): Unit =
        createXmas5x5().use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())

            val baos = ByteArrayOutputStream()

            sprite.writePNG(baos)

            assertEquals(xmas5x5Pixels, sprite.toList())

            Sprite.loadPNG({ ByteArrayInputStream(baos.toByteArray()) }).use { copy ->
                assertEquals(xmas5x5Pixels, copy.toList())

                copy.clear(BLANK)

                assertEquals(setOf(BLANK), copy.toSet())
            }

            assertEquals(xmas5x5Pixels, sprite.toList())
        }

    @Test
    fun shouldWriteToChannel(): Unit =
        createXmas5x5().use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())

            val file = Files.createTempFile("xmas5x5", ".png").toFile()
            file.deleteOnExit()

            FileOutputStream(file).use { sprite.writePNG(it.channel) }

            assertEquals(xmas5x5Pixels, sprite.toList())

            Sprite.loadPNG(file.absolutePath).use { copy ->
                assertEquals(xmas5x5Pixels, copy.toList())

                copy.clear(BLANK)

                assertEquals(setOf(BLANK), copy.toSet())
            }

            assertEquals(xmas5x5Pixels, sprite.toList())

            file.delete()
        }

    @Test
    fun shouldWriteToBase64(): Unit =
        createXmas5x5().use { sprite ->
            assertEquals(xmas5x5Pixels, sprite.toList())

            val base64 = sprite.toBase64PNG()
            assertEquals(XMAS_5_X_5_BASE64, base64)

            Sprite.loadPNGFromBase64(base64).use { copy ->
                assertEquals(xmas5x5Pixels, copy.toList())

                copy.clear(BLANK)

                assertEquals(setOf(BLANK), copy.toSet())
            }

            assertEquals(xmas5x5Pixels, sprite.toList())
        }

    private fun createXmas5x5(): Sprite = Sprite.create(5, 5).applyClosingIfFailed { clear(xmas5x5Pixels) }

    companion object {
        private val xmas5x5URL = SpriteJVMTest::class.java.getResource("/xmas_5x5.png")!!

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
            "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAPUlEQVR4XmNkYPj/n4EBRDIyMDKCmQxMYAEGoACYwQgiIYJQBQz" +
                "/IWJAJf+B+oEcdAmwlv9LGSAGMjAwAACtexCnoHY4qwAAAABJRU5ErkJggg=="
    }
}
