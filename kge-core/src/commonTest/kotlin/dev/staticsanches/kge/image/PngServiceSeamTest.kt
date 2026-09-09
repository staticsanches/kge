package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The extension-contract proof: the codec is one replaceable behavior, so a
 * decorator that overrides ONLY [PngService.decode] must be observable by
 * every caller — including [PngService.load] and encodeToBase64, whose
 * default bodies resolve [PngService.decode]/[PngService.encode]
 * through the decorated implementation.
 */
class PngServiceSeamTest :
    FunSpec({
        test("a decorator overriding decode is observable by the facade decode") {
            val original = PngService.original
            var decodeCalls = 0
            PngService.override(
                object : PngService {
                    override fun decode(
                        data: dev.staticsanches.kge.buffer.ByteBuffer,
                        sampleMode: Pixmap.SampleMode,
                        name: String?,
                    ): Sprite {
                        decodeCalls++
                        return original.decode(data, sampleMode, name)
                    }

                    override fun encode(
                        sprite: Sprite,
                    ): dev.staticsanches.kge.resource.ResourceWrapper<dev.staticsanches.kge.buffer.ByteBuffer> =
                        original.encode(sprite)
                },
            )

            tinyPngBytes.asEngineBuffer().use { png ->
                PngService.decode(png.resource).use { sprite ->
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
            }

            decodeCalls shouldBe 1
        }

        test("load resolves decode through a decorator that overrides only decode") {
            val original = PngService.original
            var decodeCalls = 0
            PngService.override(
                object : PngService {
                    override fun decode(
                        data: dev.staticsanches.kge.buffer.ByteBuffer,
                        sampleMode: Pixmap.SampleMode,
                        name: String?,
                    ): Sprite {
                        decodeCalls++
                        return original.decode(data, sampleMode, name)
                    }

                    override fun encode(
                        sprite: Sprite,
                    ): dev.staticsanches.kge.resource.ResourceWrapper<dev.staticsanches.kge.buffer.ByteBuffer> =
                        original.encode(sprite)
                },
            )

            PngService.load(PngSource.base64(tinyPngBase64)).use { sprite ->
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }

            decodeCalls shouldBe 1
        }
    })
