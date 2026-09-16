package dev.staticsanches.kge.text

import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The mono and proportional text metrics: olc's walk over a `{0, 1}` cursor and
 * size pair, tabs advancing by whole cells, and the proportional advance read
 * from the per-character spacing table.
 */
class DrawStringMetricsTest :
    FunSpec({
        test("mono size is the widest line by the tallest line, in 8px cells") {
            DrawStringService.getTextSize("AB", TAB_SIZE_IN_SPACES) shouldBe Int2D(16, 8)
        }

        test("mono size resets x and grows y on a newline") {
            DrawStringService.getTextSize("A\nB", TAB_SIZE_IN_SPACES) shouldBe Int2D(8, 16)
        }

        test("mono size advances x by tabSizeInSpaces cells on a tab") {
            DrawStringService.getTextSize("A\tB", TAB_SIZE_IN_SPACES) shouldBe Int2D(48, 8)
        }

        test("mono size rejects a non-positive tab size") {
            shouldThrow<IllegalStateException> { DrawStringService.getTextSize("A", 0) }
            shouldThrow<IllegalStateException> { DrawStringService.getTextSize("A", -1) }
        }

        test("prop size sums the per-character advances of the spacing table") {
            DrawStringService.getTextSizeProp("Ai", TAB_SIZE_IN_SPACES) shouldBe Int2D(11, 8)
        }

        test("prop size resets x, grows y on a newline and scales only y by 8 at the end") {
            DrawStringService.getTextSizeProp("Ai\nB", TAB_SIZE_IN_SPACES) shouldBe Int2D(11, 16)
        }

        test("prop size advances x by tabSizeInSpaces * 8 on a tab") {
            DrawStringService.getTextSizeProp("A\tB", TAB_SIZE_IN_SPACES) shouldBe Int2D(48, 8)
        }

        test("prop size rejects a non-positive tab size") {
            shouldThrow<IllegalStateException> { DrawStringService.getTextSizeProp("A", 0) }
            shouldThrow<IllegalStateException> { DrawStringService.getTextSizeProp("A", -1) }
        }
    })

private const val TAB_SIZE_IN_SPACES = 4
