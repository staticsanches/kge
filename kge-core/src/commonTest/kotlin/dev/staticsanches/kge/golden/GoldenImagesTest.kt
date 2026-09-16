package dev.staticsanches.kge.golden

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

class GoldenImagesTest :
    FunSpec({
        test("the smoke golden exposes its name, dimensions and RGBA bytes") {
            val golden = GoldenImages.byName.getValue("harness/smoke")
            golden.name shouldBe "harness/smoke"
            golden.width shouldBe 2
            golden.height shouldBe 2

            val rgba = Base64.Default.decode(golden.rgbaBase64)
            rgba.size shouldBe 16
            rgba.copyOfRange(0, 4) shouldBe byteArrayOf(255.toByte(), 0, 0, 255.toByte())
            rgba.copyOfRange(8, 12) shouldBe byteArrayOf(0, 0, 255.toByte(), 128.toByte())
        }
    })
