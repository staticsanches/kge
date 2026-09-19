package dev.staticsanches.kge.font.roboto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

class BundledFontsTest :
    FunSpec({
        test("Roboto variable font decodes to the pinned bytes") {
            val bytes = decode(Roboto.variableFont)
            bytes.size shouldBe 488_584
            bytes.copyOfRange(0, 4).map { it.toInt() } shouldBe listOf(0, 1, 0, 0) // sfnt 0x00010000
            fnv1a64(bytes) shouldBe -4_869_763_841_651_235_108L
        }

        test("Roboto Mono variable font decodes to the pinned bytes") {
            val bytes = decode(RobotoMono.variableFont)
            bytes.size shouldBe 183_700
            bytes.copyOfRange(0, 4).map { it.toInt() } shouldBe listOf(0, 1, 0, 0)
            fnv1a64(bytes) shouldBe 17_806_645_914_498_892L
        }

        test("the payload is chunked at 32768 characters, remainder last") {
            Roboto.variableFont.size shouldBe 20
            Roboto.variableFont.dropLast(1).forEach { it.length shouldBe 32_768 }
            Roboto.variableFont.last().length shouldBe 28_856
            RobotoMono.variableFont.size shouldBe 8
            RobotoMono.variableFont.dropLast(1).forEach { it.length shouldBe 32_768 }
            RobotoMono.variableFont.last().length shouldBe 15_560
        }

        test("provenance constants identify the shipped builds") {
            Roboto.FAMILY shouldBe "Roboto"
            Roboto.VERSION shouldBe "3.015"
            Roboto.LICENSE_ID shouldBe "OFL-1.1"
            Roboto.SOURCE shouldBe "https://github.com/google/fonts/tree/main/ofl/roboto"
            RobotoMono.FAMILY shouldBe "Roboto Mono"
            RobotoMono.VERSION shouldBe "3.001"
            RobotoMono.LICENSE_ID shouldBe "OFL-1.1"
            RobotoMono.SOURCE shouldBe "https://github.com/google/fonts/tree/main/ofl/robotomono"
        }

        test("license texts are the verbatim upstream OFL files") {
            Roboto.licenseText.length shouldBe 4_394
            Roboto.licenseText.startsWith("Copyright 2011 The Roboto Project Authors") shouldBe true
            Roboto.licenseText.contains("SIL Open Font License, Version 1.1") shouldBe true
            RobotoMono.licenseText.length shouldBe 4_395
            RobotoMono.licenseText.startsWith("Copyright 2015 The Roboto Mono Project Authors") shouldBe true
        }
    })

private fun decode(chunks: List<String>): ByteArray = Base64.Default.decode(chunks.joinToString(""))

/** FNV-1a 64 (offset basis 0xcbf29ce484222325, prime 0x100000001b3). */
private fun fnv1a64(bytes: ByteArray): Long {
    var hash = -3_750_763_034_362_895_579L
    for (byte in bytes) hash = (hash xor (byte.toLong() and 0xFF)) * 1_099_511_628_211L
    return hash
}
