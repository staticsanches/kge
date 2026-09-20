package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.math.vector.Float2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The shaping contract pinned on the shipped Roboto fixture at its default
 * instance: ids, advances, offsets, code-point clusters and the kerning
 * relation, identical on JVM and both web targets.
 */
class ShapingTest :
    FunSpec({
        test("shaping a mixed run yields the pinned ids, advances, offsets and clusters") {
            // the fixture identity, so a font bump fails here instead of re-pinning
            Roboto.FAMILY shouldBe "Roboto"
            Roboto.VERSION shouldBe "3.015"

            Font.load(Roboto.variableFont).use { font ->
                val glyphs = font.shape("AV To Wave 123", 16).glyphs

                glyphs.map { it.glyphId } shouldBe
                    listOf(37, 58, 4, 56, 83, 4, 59, 69, 90, 73, 4, 21, 22, 23)
                glyphs.map { it.advance.x } shouldBe
                    listOf(
                        9.765625f,
                        10.1875f,
                        3.65625f,
                        8.78125f,
                        9.125f,
                        3.96875f,
                        13.953125f,
                        8.59375f,
                        7.65625f,
                        8.484375f,
                        3.96875f,
                        9f,
                        9f,
                        9f,
                    )
                glyphs.map { it.advance.y } shouldBe List(14) { 0f }
                glyphs.map { it.offset } shouldBe List(14) { Float2D(0f, 0f) }
                glyphs.map { it.cluster } shouldBe (0..13).toList()
            }
        }

        test("kerning is applied by default") {
            Font.load(Roboto.variableFont).use { font ->
                val alone = font.shape("A", 16).glyphs.single()
                val kerned = font.shape("AV", 16).glyphs.first()

                alone.advance.x shouldBe 10.4375f
                kerned.advance.x shouldBe 9.765625f
                (kerned.advance.x < alone.advance.x) shouldBe true
            }
        }

        test("an accented character keeps code-point clusters") {
            Font.load(Roboto.variableFont).use { font ->
                val glyphs = font.shape("AéB", 16).glyphs

                glyphs.map { it.glyphId } shouldBe listOf(37, 703, 38)
                glyphs.map { it.cluster } shouldBe listOf(0, 1, 2)
            }
        }

        test("a non-BMP character is one code point and one cluster") {
            Font.load(Roboto.variableFont).use { font ->
                val glyphs = font.shape("A\uD83D\uDE00B", 16).glyphs

                glyphs.map { it.cluster } shouldBe listOf(0, 1, 2)
            }
        }

        test("shaping from base64 matches shaping from decoded bytes") {
            val decoded = Font.load(robotoFontBytes()).use { it.shape("AV To Wave 123", 16) }

            Font.load(Roboto.variableFont).use { font ->
                font.shape("AV To Wave 123", 16) shouldBe decoded
            }
        }

        test("the run carries the pinned 16 px metrics") {
            Font.load(Roboto.variableFont).use { font ->
                val metrics = font.shape("A", 16).metrics

                metrics.ascender shouldBe 14.84375f
                metrics.descender shouldBe -3.90625f
                metrics.lineGap shouldBe 0f
            }
        }
    })
