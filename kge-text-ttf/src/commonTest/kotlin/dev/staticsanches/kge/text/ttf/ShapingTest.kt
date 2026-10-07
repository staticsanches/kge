package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.math.vector.Float2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

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

            withRobotoFace { face ->
                val glyphs = face.shape("AV To Wave 123".toCodePoints(), 16)

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
            withRobotoFace { face ->
                val alone = face.shape("A".toCodePoints(), 16).single()
                val kerned = face.shape("AV".toCodePoints(), 16).first()

                alone.advance.x shouldBe 10.4375f
                kerned.advance.x shouldBe 9.765625f
                (kerned.advance.x < alone.advance.x) shouldBe true
            }
        }

        test("an accented character keeps code-point clusters") {
            withRobotoFace { face ->
                val glyphs = face.shape("AéB".toCodePoints(), 16)

                glyphs.map { it.glyphId } shouldBe listOf(37, 703, 38)
                glyphs.map { it.cluster } shouldBe listOf(0, 1, 2)
            }
        }

        test("a non-BMP character is one code point and one cluster") {
            withRobotoFace { face ->
                val glyphs = face.shape("A\uD83D\uDE00B".toCodePoints(), 16)

                glyphs.map { it.cluster } shouldBe listOf(0, 1, 2)
            }
        }

        test("shaping from base64 matches shaping from decoded bytes") {
            val decoded = withRobotoFace { it.shape("AV To Wave 123".toCodePoints(), 16) }

            withRobotoFace(Base64.decode(Roboto.romanFont.joinToString(""))) { face ->
                face.shape("AV To Wave 123".toCodePoints(), 16) shouldBe decoded
            }
        }

        test("the run carries the pinned 16 px metrics") {
            withRobotoFace { face ->
                val metrics = face.metrics(16)

                metrics.ascender shouldBe 14.84375f
                metrics.descender shouldBe -3.90625f
                metrics.lineGap shouldBe 0f
            }
        }

        test("a combining mark carries its shaped offset and zero advance") {
            withRobotoFace { face ->
                val above = face.shape("x\u0301".toCodePoints(), 16)[1]
                above.offset shouldBe Float2D(0.453125f, -0.078125f)
                above.advance shouldBe Float2D(0f, 0f)

                val below = face.shape("q\u0323".toCodePoints(), 16)[1]
                below.offset shouldBe Float2D(2.734375f, -3.171875f)
                below.advance shouldBe Float2D(0f, 0f)
            }
        }
    })
