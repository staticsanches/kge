package dev.staticsanches.kge.image

import dev.staticsanches.kge.rasterizer.service.DrawService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The pixel-mode write policies and the blend resolution math: [Normal]
 * writes verbatim, [Mask] writes only opaque colors, [Alpha] blends by
 * `a = (color.a / 255f) * blendFactor`, truncating, to an always-opaque
 * result, and [Custom] resolves through [Pixel.Mode.Custom.apply]. Blends
 * are exercised through [DrawService.draw] on a 1x1 sprite; the out-of-bounds
 * policy lives in RasterizerTest.
 */
class ModeTest :
    FunSpec({
        fun onePixel(): Sprite = SpriteService.create(1, 1, Pixmap.SampleMode.NORMAL, null)

        test("Normal writes verbatim, including a fully transparent pixel") {
            onePixel().use { target ->
                val transparent = Pixel.rgba(9, 8, 7, 0)
                DrawService.draw(target, 0, 0, transparent, Pixel.Mode.Normal) shouldBe true
                target.get(0, 0) shouldBe transparent
            }
        }

        test("Mask drops an alpha < 255 pixel and writes an opaque one") {
            onePixel().use { target ->
                DrawService.draw(target, 0, 0, Colors.RED, Pixel.Mode.Normal)

                DrawService.draw(target, 0, 0, Pixel.rgba(1, 2, 3, 4), Pixel.Mode.Mask) shouldBe false
                target.get(0, 0) shouldBe Colors.RED

                DrawService.draw(target, 0, 0, Pixel.rgba(1, 2, 3, 255), Pixel.Mode.Mask) shouldBe true
                target.get(0, 0) shouldBe Pixel.rgba(1, 2, 3, 255)
            }
        }

        test("Alpha(0.5) of opaque red over white truncates to (255,127,127) and is opaque") {
            onePixel().use { target ->
                DrawService.draw(target, 0, 0, Colors.WHITE, Pixel.Mode.Normal)

                DrawService.draw(target, 0, 0, Colors.RED, Pixel.Mode.Alpha(0.5f)) shouldBe true
                target.get(0, 0) shouldBe Pixel.rgba(255, 127, 127, 255)
            }
        }

        test("Alpha blends the source alpha and always resolves to an opaque pixel") {
            onePixel().use { target ->
                DrawService.draw(target, 0, 0, Colors.WHITE, Pixel.Mode.Normal)

                val halfAlphaRed = Pixel.rgba(255, 0, 0, 128)
                DrawService.draw(target, 0, 0, halfAlphaRed, Pixel.Mode.Alpha(0.5f)) shouldBe true
                val blended = target.get(0, 0)
                blended.a shouldBe 255
                blended shouldBe Pixel.rgba(255, 191, 191, 255)
            }
        }

        test("Alpha over a transparent old pixel still resolves opaque") {
            onePixel().use { target ->
                DrawService.draw(target, 0, 0, Colors.TRANSPARENT, Pixel.Mode.Normal)

                DrawService.draw(target, 0, 0, Colors.RED, Pixel.Mode.Alpha(0.5f)) shouldBe true
                target.get(0, 0) shouldBe Pixel.rgba(127, 0, 0, 255)
            }
        }

        test("Alpha clamps its blend factor at construction") {
            Pixel.Mode.Alpha(2f).blendFactor shouldBe 1f
            Pixel.Mode.Alpha(-1f).blendFactor shouldBe 0f
            Pixel.Mode.Alpha(2f) shouldBe Pixel.Mode.Alpha(1f)
            Pixel.Mode.Alpha(0.5f).blendFactor shouldBe 0.5f
            Pixel.Mode.Alpha().blendFactor shouldBe 1f
        }

        test("Custom sees the coordinates, the new pixel and the stored old pixel") {
            onePixel().use { target ->
                DrawService.draw(target, 0, 0, Colors.WHITE, Pixel.Mode.Normal)

                var seen: Pixel.Mode.Custom? = null
                DrawService.draw(
                    target,
                    0,
                    0,
                    Pixel.rgba(9, 0, 0, 255),
                    object : Pixel.Mode.Custom {
                        override fun apply(
                            x: Int,
                            y: Int,
                            newPixel: Pixel,
                            oldPixel: Pixel,
                        ): Pixel {
                            seen = this
                            x shouldBe 0
                            y shouldBe 0
                            newPixel shouldBe Pixel.rgba(9, 0, 0, 255)
                            oldPixel shouldBe Colors.WHITE
                            return Pixel.rgba(1, 2, 3, 4)
                        }
                    },
                ) shouldBe true

                target.get(0, 0) shouldBe Pixel.rgba(1, 2, 3, 4)
                seen shouldNotBe null
            }
        }
    })
