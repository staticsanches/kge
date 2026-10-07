package dev.staticsanches.kge.text.ttf

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
            withRobotoFace { face ->
                val small = face.metrics(16)
                val large = face.metrics(32)

                large.ascender shouldBe 29.6875f
                large.descender shouldBe -7.8125f
                large.lineGap shouldBe 0f
                large.ascender shouldBe small.ascender * 2f
                large.descender shouldBe small.descender * 2f
            }
        }

        test("a non-positive size is rejected") {
            withRobotoFace { face ->
                // the surviving size guard sits at the text walk's entry
                shouldThrow<IllegalArgumentException> { measureText(face, "A", 0, 1) }
                shouldThrow<IllegalArgumentException> { measureText(face, "A", -8, 1) }
            }
        }
    })
