package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PixelTest : FunSpec({
    context("construction and channels") {
        test("rgba(r, g, b, a) exposes the four channels") {
            val pixel = Pixel.rgba(0x12, 0x34, 0x56, 0x78)
            pixel.r shouldBe 0x12
            pixel.g shouldBe 0x34
            pixel.b shouldBe 0x56
            pixel.a shouldBe 0x78
        }

        test("rgba(r, g, b) uses opaque alpha") {
            val pixel = Pixel.rgba(0x12, 0x34, 0x56)
            pixel.a shouldBe 0xFF
        }

        test("rgba(UInt) interprets the value as RRGGBBAA") {
            val pixel = Pixel.rgba(0x12345678u)
            pixel.r shouldBe 0x12
            pixel.g shouldBe 0x34
            pixel.b shouldBe 0x56
            pixel.a shouldBe 0x78
        }

        test("the packed value stores R,G,B,A in little-endian byte order") {
            // nativeRGBA = R | G<<8 | B<<16 | A<<24 → memory bytes read R,G,B,A
            Pixel.rgba(0x12, 0x34, 0x56, 0x78).nativeRGBA shouldBe 0x78563412
        }

        test("rgba exposes the canonical RRGGBBAA value") {
            Pixel.rgba(0x12, 0x34, 0x56, 0x78).rgba shouldBe 0x12345678u
            Pixel.rgba(0xFF000000u).rgba shouldBe 0xFF000000u
        }
    }

    context("value semantics") {
        test("equal colors are equal and share the hashCode") {
            Pixel.rgba(1, 2, 3, 4) shouldBe Pixel.rgba(1, 2, 3, 4)
            Pixel.rgba(1, 2, 3, 4).hashCode() shouldBe Pixel.rgba(1, 2, 3, 4).hashCode()
        }

        test("colors differing in any channel are unequal") {
            Pixel.rgba(1, 2, 3, 4) shouldNotBe Pixel.rgba(2, 2, 3, 4)
            Pixel.rgba(1, 2, 3, 4) shouldNotBe Pixel.rgba(1, 3, 3, 4)
            Pixel.rgba(1, 2, 3, 4) shouldNotBe Pixel.rgba(1, 2, 4, 4)
            Pixel.rgba(1, 2, 3, 4) shouldNotBe Pixel.rgba(1, 2, 3, 5)
        }

        test("destructures into the four channels") {
            val (r, g, b, a) = Pixel.rgba(0x12, 0x34, 0x56, 0x78)
            r shouldBe 0x12
            g shouldBe 0x34
            b shouldBe 0x56
            a shouldBe 0x78
        }

        test("toString renders fixed uppercase hex") {
            Pixel.rgba(0x12, 0x34, 0x56, 0x78).toString() shouldBe "#12345678"
            Pixel.rgba(0, 0, 0).toString() shouldBe "#000000FF"
            Pixel.rgba(255, 255, 255).toString() shouldBe "#FFFFFFFF"
        }
    }

    context("arithmetic") {
        test("plus adds the RGB channels and keeps the receiver's alpha") {
            Pixel.rgba(10, 20, 30, 5) + Pixel.rgba(1, 2, 3, 0) shouldBe Pixel.rgba(11, 22, 33, 5)
        }

        test("plus saturates channels at 255") {
            Pixel.rgba(200, 250, 255, 5) + Pixel.rgba(100, 100, 0, 0) shouldBe Pixel.rgba(255, 255, 255, 5)
        }

        test("minus clamps at 0 instead of wrapping (main masked, olc clamps)") {
            Pixel.rgba(10, 30, 200, 5) - Pixel.rgba(20, 30, 100, 0) shouldBe Pixel.rgba(0, 0, 100, 5)
        }

        test("times scales the RGB channels and truncates") {
            Pixel.rgba(250, 100, 0, 5) * 0.5f shouldBe Pixel.rgba(125, 50, 0, 5)
        }

        test("times saturates when a scaled channel exceeds 255") {
            Pixel.rgba(250, 100, 0, 5) * 1.1f shouldBe Pixel.rgba(255, 110, 0, 5)
        }

        test("div divides the RGB channels and truncates") {
            Pixel.rgba(200, 100, 50, 5) / 2f shouldBe Pixel.rgba(100, 50, 25, 5)
        }

        test("div by zero saturates the channels to 255") {
            Pixel.rgba(200, 100, 50, 5) / 0f shouldBe Pixel.rgba(255, 255, 255, 5)
        }

        test("div by zero splits per channel: positive saturates, zero channel stays 0") {
            // 0/0 = NaN → 0; 100/0 and 50/0 = +Inf → 255
            Pixel.rgba(0, 100, 50, 5) / 0f shouldBe Pixel.rgba(0, 255, 255, 5)
        }

        test("div by NaN yields 0") {
            Pixel.rgba(200, 100, 50, 5) / Float.NaN shouldBe Pixel.rgba(0, 0, 0, 5)
        }

        test("div by a negative factor yields 0") {
            Pixel.rgba(200, 100, 50, 5) / -2f shouldBe Pixel.rgba(0, 0, 0, 5)
        }

        test("times by zero yields 0") {
            Pixel.rgba(200, 100, 50, 5) * 0f shouldBe Pixel.rgba(0, 0, 0, 5)
        }

        test("times by NaN yields 0") {
            Pixel.rgba(200, 100, 50, 5) * Float.NaN shouldBe Pixel.rgba(0, 0, 0, 5)
        }

        test("times by a negative factor yields 0") {
            Pixel.rgba(200, 100, 50, 5) * -1f shouldBe Pixel.rgba(0, 0, 0, 5)
        }

        test("inv inverts the RGB channels and keeps the alpha") {
            Pixel.rgba(10, 20, 30, 5).inv() shouldBe Pixel.rgba(245, 235, 225, 5)
        }

        test("lerp returns this at t=0, end at t=1 and truncates in between") {
            val start = Pixel.rgba(0, 0, 0, 5)
            val end = Pixel.rgba(255, 255, 255, 5)
            start.lerp(end, 0f) shouldBe start
            start.lerp(end, 1f) shouldBe end
            start.lerp(end, 0.5f) shouldBe Pixel.rgba(127, 127, 127, 5)
        }

        test("lerp keeps the receiver's alpha") {
            Pixel.rgba(0, 0, 0, 5).lerp(Pixel.rgba(255, 255, 255, 0), 0.5f).a shouldBe 5
        }
    }
})
