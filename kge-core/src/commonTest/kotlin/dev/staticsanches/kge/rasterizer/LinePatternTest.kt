package dev.staticsanches.kge.rasterizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LinePatternTest :
    FunSpec({
        test("Filled draws every pixel") {
            val pattern = LinePattern.Filled
            repeat(6) { pattern.shouldDrawPixel() shouldBe true }
        }

        test("Empty draws no pixel") {
            val pattern = LinePattern.Empty
            repeat(6) { pattern.shouldDrawPixel() shouldBe false }
        }

        test("a fresh Dotted alternates starting with a drawn pixel") {
            val pattern = LinePattern.Dotted()
            val sequence = List(6) { pattern.shouldDrawPixel() }
            sequence shouldBe listOf(true, false, true, false, true, false)
        }

        test("a Dotted built with an initial unset state skips the first pixel") {
            val pattern = LinePattern.Dotted(false)
            val sequence = List(4) { pattern.shouldDrawPixel() }
            sequence shouldBe listOf(false, true, false, true)
        }

        test("a Custom pattern is honored") {
            var calls = 0
            val pattern =
                object : LinePattern.Custom {
                    override fun shouldDrawPixel(): Boolean = calls++ % 2 == 0
                }
            val sequence = List(4) { pattern.shouldDrawPixel() }
            sequence shouldBe listOf(true, false, true, false)
        }
    })
