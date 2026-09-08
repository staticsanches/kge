package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlin.io.encoding.Base64

/**
 * S5 encodeToBase64 + the S5 base64 add-time proof: the stdlib
 * `kotlin.io.encoding.Base64` (stable since Kotlin 2.2) round-trips bytes on
 * every target, the codec's payload decodes back to the source pixels, and
 * the payload really is a PNG (signature bytes after decoding).
 */
class PngEncodeToBase64Test :
    FunSpec({
        test("stdlib base64 round-trips bytes on this target") {
            val payload = Base64.Default.encode(tinyPngBytes)
            Base64.Default.decode(payload).toList() shouldBe tinyPngBytes.toList()
        }

        test("encodeToBase64 decodes back to the same pixels") {
            val sprite = distinctSprite()
            try {
                val payload = PngService.encodeToBase64(sprite)
                payload.shouldNotBeEmpty()

                Base64.Default.decode(payload).asEngineBuffer().use { png ->
                    PngService.decode(png.resource).use { back ->
                        back.rowMajorPixels() shouldBe sprite.rowMajorPixels()
                    }
                }
            } finally {
                sprite.close()
            }
        }

        test("the base64 payload is a real PNG") {
            distinctSprite().use { sprite ->
                val payload = PngService.encodeToBase64(sprite)

                Base64.Default
                    .decode(payload)
                    .take(8)
                    .map { it.toInt() and 0xFF } shouldBe
                    listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            }
        }
    })
