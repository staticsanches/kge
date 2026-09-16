package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.io.encoding.Base64

private const val SMOKE = "harness/smoke"
private const val OVERSIZED = "harness/oversized"

private val TOKEN = Regex("""(\d+)x(\d+):([A-Za-z0-9+/=]+)""")

class GoldenMatcherTest :
    FunSpec({
        test("a surface matching the smoke golden passes") {
            surfaceFrom(GoldenImages.byName.getValue(SMOKE)).use { surface ->
                surface.shouldMatchGolden(SMOKE)
            }
        }

        test("one changed cell fails with the name, dimensions, count and a decodable actual token") {
            surfaceFrom(GoldenImages.byName.getValue(SMOKE)).use { surface ->
                surface.set(0, 0, Pixel.rgba(0, 0, 0, 0))

                val message = shouldThrow<AssertionError> { surface.shouldMatchGolden(SMOKE) }.message.orEmpty()

                message shouldContain SMOKE
                message shouldContain "2x2"
                message shouldContain "1 mismatch"
                message shouldNotContain "(0, 0)"

                val (dimensions, actual) = decodeToken(tokenFrom(message))
                dimensions shouldBe "2x2"
                actual shouldBe surface.rgbaBytes()
            }
        }

        test("two changed cells report a count of 2") {
            surfaceFrom(GoldenImages.byName.getValue(SMOKE)).use { surface ->
                surface.set(0, 0, Pixel.rgba(0, 0, 0, 0))
                surface.set(1, 1, Pixel.rgba(0xFF, 0xFF, 0xFF, 0xFF))

                val message = shouldThrow<AssertionError> { surface.shouldMatchGolden(SMOKE) }.message.orEmpty()

                message shouldContain "2 mismatches"
            }
        }

        test("a dimension mismatch reports both sizes and no token") {
            SpriteService.create(3, 2, Pixmap.SampleMode.NORMAL, null).use { surface ->
                val message = shouldThrow<AssertionError> { surface.shouldMatchGolden(SMOKE) }.message.orEmpty()

                message shouldContain "2x2"
                message shouldContain "3x2"
                TOKEN.containsMatchIn(message) shouldBe false
            }
        }

        test("an unknown name lists the available goldens") {
            surfaceFrom(GoldenImages.byName.getValue(SMOKE)).use { surface ->
                val message =
                    shouldThrow<AssertionError> {
                        surface.shouldMatchGolden("missing/case")
                    }.message.orEmpty()

                message shouldContain "missing/case"
                message shouldContain SMOKE
            }
        }

        test("an alpha-only change on a translucent pixel is a mismatch") {
            surfaceFrom(GoldenImages.byName.getValue(SMOKE)).use { surface ->
                surface.set(0, 1, Pixel.rgba(0, 0, 255, 127))

                val message = shouldThrow<AssertionError> { surface.shouldMatchGolden(SMOKE) }.message.orEmpty()

                message shouldContain "1 mismatch"
            }
        }

        test("above the token budget the actual token is omitted with the cell count") {
            surfaceFrom(GoldenImages.byName.getValue(OVERSIZED)).use { surface ->
                surface.set(0, 0, Pixel.rgba(0, 0, 0, 0))

                val message = shouldThrow<AssertionError> { surface.shouldMatchGolden(OVERSIZED) }.message.orEmpty()

                message shouldContain OVERSIZED
                message shouldContain "1 mismatch"
                message shouldContain "4160 cells"
                message shouldContain "4096"
                TOKEN.containsMatchIn(message) shouldBe false
            }
        }
    })

private fun surfaceFrom(golden: GoldenImage): Sprite {
    val bytes = Base64.Default.decode(golden.rgbaBase64)
    return SpriteService
        .create(golden.width, golden.height, Pixmap.SampleMode.NORMAL, golden.name)
        .applyClosingIfFailed {
            for (y in 0 until golden.height) {
                for (x in 0 until golden.width) {
                    set(x, y, Pixel.fromNativeRGBA(nativeAt(bytes, (y * golden.width + x) * 4)))
                }
            }
        }
}

private fun tokenFrom(message: String): String =
    TOKEN.find(message)?.value ?: error("no actual token in message:\n$message")

private fun decodeToken(token: String): Pair<String, ByteArray> {
    val match = TOKEN.matchEntire(token) ?: error("malformed token: $token")
    return "${match.groupValues[1]}x${match.groupValues[2]}" to Base64.Default.decode(match.groupValues[3])
}

private fun Pixmap.rgbaBytes(): ByteArray {
    val bytes = ByteArray(width * height * 4)
    var index = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val native = get(x, y).nativeRGBA
            bytes[index++] = (native and 0xFF).toByte()
            bytes[index++] = ((native ushr 8) and 0xFF).toByte()
            bytes[index++] = ((native ushr 16) and 0xFF).toByte()
            bytes[index++] = ((native ushr 24) and 0xFF).toByte()
        }
    }
    return bytes
}

private fun nativeAt(
    bytes: ByteArray,
    offset: Int,
): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
