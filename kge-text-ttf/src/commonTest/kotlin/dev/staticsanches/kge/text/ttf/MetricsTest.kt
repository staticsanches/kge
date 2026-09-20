package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The metrics a run carries: the pinned values and the scale relation that
 * makes `sizePx` the only input, plus the fail-fast on a non-positive size.
 */
class MetricsTest :
    FunSpec({
        test("the metrics scale with sizePx") {
            Font.load(Roboto.variableFont).use { font ->
                val small = font.shape("A", 16).metrics
                val large = font.shape("A", 32).metrics

                large.ascender shouldBe 29.6875f
                large.descender shouldBe -7.8125f
                large.lineGap shouldBe 0f
                large.ascender shouldBe small.ascender * 2f
                large.descender shouldBe small.descender * 2f
            }
        }

        test("a non-positive size is rejected") {
            Font.load(Roboto.variableFont).use { font ->
                shouldThrow<IllegalArgumentException> { font.shape("A", 0) }
                shouldThrow<IllegalArgumentException> { font.shape("A", -8) }
            }
        }
    })
