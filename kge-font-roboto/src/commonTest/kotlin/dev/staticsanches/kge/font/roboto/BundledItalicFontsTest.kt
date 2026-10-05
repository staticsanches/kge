package dev.staticsanches.kge.font.roboto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BundledItalicFontsTest :
    FunSpec({
        test("Roboto italic font decodes to the pinned bytes") {
            val bytes = decode(Roboto.italicFont)
            bytes.size shouldBe 530_944
            bytes.copyOfRange(0, 4).map { it.toInt() } shouldBe listOf(0, 1, 0, 0) // sfnt 0x00010000
            fnv1a64(bytes) shouldBe 4_654_176_676_351_108_715L
        }

        test("Roboto Mono italic font decodes to the pinned bytes") {
            val bytes = decode(RobotoMono.italicFont)
            bytes.size shouldBe 196_792
            bytes.copyOfRange(0, 4).map { it.toInt() } shouldBe listOf(0, 1, 0, 0)
            fnv1a64(bytes) shouldBe 3_367_967_902_583_743_594L
        }

        test("the italic payloads are chunked at 32768 characters, remainder last") {
            Roboto.italicFont.size shouldBe 22
            Roboto.italicFont.dropLast(1).forEach { it.length shouldBe 32_768 }
            Roboto.italicFont.last().length shouldBe 19_800
            RobotoMono.italicFont.size shouldBe 9
            RobotoMono.italicFont.dropLast(1).forEach { it.length shouldBe 32_768 }
            RobotoMono.italicFont.last().length shouldBe 248
        }

        test("each family ships two distinct faces, not one payload twice") {
            decode(Roboto.italicFont).contentEquals(decode(Roboto.romanFont)) shouldBe false
            decode(RobotoMono.italicFont).contentEquals(decode(RobotoMono.romanFont)) shouldBe false
        }
    })
