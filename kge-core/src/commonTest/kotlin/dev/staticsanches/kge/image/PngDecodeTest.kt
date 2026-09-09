package dev.staticsanches.kge.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Decode: the codec turns the PNG bytes in an engine buffer into a
 * [Sprite]. The byte source is a wrapper the caller owns; decode itself
 * allocates the surface storage (or zero-copy-wraps it on JVM) and never
 * touches the input's lifetime.
 */
class PngDecodeTest :
    FunSpec({
        test("decode returns the 2x2 fixture with the expected row-major pixels") {
            tinyPngBytes.asEngineBuffer().use { png ->
                PngService.decode(png.resource).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                    sprite.sampleMode shouldBe Pixmap.SampleMode.NORMAL
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
            }
        }

        test("decode lands the sampleMode and name on the sprite") {
            tinyPngBytes.asEngineBuffer().use { png ->
                PngService.decode(png.resource, Pixmap.SampleMode.CLAMP, "tiny").use { sprite ->
                    sprite.sampleMode shouldBe Pixmap.SampleMode.CLAMP
                    sprite.toString() shouldContain "tiny"
                }
            }
        }

        test("a null name keeps the default toString") {
            tinyPngBytes.asEngineBuffer().use { png ->
                PngService.decode(png.resource).use { sprite ->
                    sprite.name shouldBe null
                    sprite.toString() shouldBe "Sprite(2x2, NORMAL)"
                }
            }
        }

        test("decode throws on bytes that are not a PNG") {
            notAPngBytes.asEngineBuffer().use { garbage ->
                shouldThrow<Throwable> { PngService.decode(garbage.resource) }
            }
        }
    })
