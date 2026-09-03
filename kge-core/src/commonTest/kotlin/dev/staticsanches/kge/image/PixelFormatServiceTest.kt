package dev.staticsanches.kge.image

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The extension-contract proof for the engine's first real service: the pixel
 * display format. The default is the engine-fixed HEX representation; an
 * override — such as the test-local RGBA format below — provably changes every
 * `Pixel.toString()` output. [PixelFormatService.original] is the engine
 * default, so a decorator can wrap it.
 */
class PixelFormatServiceTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        test("the facade resolves the default hex format") {
            PixelFormatService.format(Pixel.rgba(0x12, 0x34, 0x56, 0x78)) shouldBe "#12345678"
            PixelFormatService.format(Pixel.rgba(0, 0, 0)) shouldBe "#000000FF"
            PixelFormatService.format(Pixel.rgba(255, 255, 255)) shouldBe "#FFFFFFFF"
        }

        test("toString renders through the service") {
            PixelFormatService.override(RgbaPixelFormat)

            Pixel.rgba(18, 52, 86, 120).toString() shouldBe "rgba(18, 52, 86, 120)"
        }

        test("a decorator delegating to original wraps the engine default") {
            val original = PixelFormatService.original
            PixelFormatService.override(
                object : PixelFormatService {
                    override fun format(pixel: Pixel): String = "decorated(${original.format(pixel)})"
                },
            )

            PixelFormatService.format(Pixel.rgba(0x12, 0x34, 0x56, 0x78)) shouldBe "decorated(#12345678)"
        }

        test("a later override supersedes the previous one") {
            PixelFormatService.override(RgbaPixelFormat)
            PixelFormatService.override(
                object : PixelFormatService {
                    override fun format(pixel: Pixel): String = "second:${pixel.rgba.toString(16)}"
                },
            )

            PixelFormatService.format(Pixel.rgba(0x12, 0x34, 0x56, 0x78)) shouldBe "second:12345678"
        }

        test("resetAll restores the engine default") {
            PixelFormatService.override(RgbaPixelFormat)

            KGEOverridable.Proxy.resetAll()

            PixelFormatService.format(Pixel.rgba(0x12, 0x34, 0x56, 0x78)) shouldBe "#12345678"
        }
    })

/** A test-local alternate representation, never shipped: the override proof. */
private object RgbaPixelFormat : PixelFormatService {
    override fun format(pixel: Pixel): String = "rgba(${pixel.r}, ${pixel.g}, ${pixel.b}, ${pixel.a})"
}
