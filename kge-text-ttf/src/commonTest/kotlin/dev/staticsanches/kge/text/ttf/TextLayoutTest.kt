package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.math.vector.Float2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The shaped-layout values: a glyph pairs the post-shaping id and code point
 * cluster with a [Float2D] offset/advance, and the metrics are structural.
 */
class TextLayoutTest :
    FunSpec({
        test("a shaped glyph is a structural value with Float2D offset and advance") {
            val offset: Float2D = Float2D(1.5f, -0.5f)
            val advance: Float2D = Float2D(9.765625f, 0f)
            val glyph = ShapedGlyph(glyphId = 37, cluster = 0, offset = offset, advance = advance)

            glyph.glyphId shouldBe 37
            glyph.cluster shouldBe 0
            glyph.offset shouldBe offset
            glyph.advance shouldBe advance
            glyph shouldBe ShapedGlyph(37, 0, offset, advance)
            glyph.copy(cluster = 2).cluster shouldBe 2
        }

        test("text metrics are a structural value") {
            TextMetrics(1f, 2f, 3f) shouldBe TextMetrics(1f, 2f, 3f)
            TextMetrics(1f, 2f, 3f) shouldNotBe TextMetrics(1f, 2f, 4f)
        }
    })
