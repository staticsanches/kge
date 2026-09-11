package dev.staticsanches.kge.image

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * A [Pixmap] is a [dev.staticsanches.kge.rasterizer.Viewport.Bounded] over its
 * dimensions, and [dev.staticsanches.kge.rasterizer.service.OutlineService
 * .drawLine] clips through the active clip seam before it walks.
 */
class RasterizerClipTest :
    FunSpec({
        fun target(
            width: Int = 2,
            height: Int = 2,
            mode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        ): Sprite =
            SpriteService
                .create(width, height, mode, null)
                .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

        fun painted(t: Sprite): Set<Pair<Int, Int>> =
            (0 until t.height)
                .flatMap { y -> (0 until t.width).map { x -> x to y } }
                .filter { (x, y) -> t.get(x, y) != Colors.TRANSPARENT }
                .toSet()

        test("a Sprite is a Bounded viewport over its dimensions") {
            target(width = 4, height = 3).use { t ->
                t.lowerBoundInclusive shouldBe Int2D(0, 0)
                t.upperBoundExclusive shouldBe Int2D(4, 3)
                t.contains(3, 2) shouldBe true
                t.contains(4, 2) shouldBe false
            }
        }

        test("drawLine clips before walking, so an OOB endpoint re-walks the visible span") {
            target(width = 4, height = 4).use { t ->
                Rasterizer.drawLine(t, -3, -1, 3, 2, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 0, 1 to 1, 2 to 1, 3 to 2)
            }
        }

        test("a trivially rejected drawLine paints nothing and never throws") {
            target(width = 4, height = 4).use { t ->
                Rasterizer.drawLine(t, 5, 5, 6, 6, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                Rasterizer.drawLine(t, -5, -5, -1, -1, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                painted(t) shouldBe emptySet()
            }
        }

        test("a steep line whose walk starts at an OOB endpoint re-walks the clipped span") {
            target(width = 4, height = 4).use { t ->
                Rasterizer.drawLine(t, 1, -2, 2, 3, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(1 to 0, 1 to 1, 1 to 2, 2 to 3)
            }
        }

        test("a partial-OOB vertical line paints the visible range") {
            target(width = 4, height = 4).use { t ->
                Rasterizer.drawLine(t, 1, -2, 1, 2, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(1 to 0, 1 to 1, 1 to 2)
            }
        }

        test("a partial-OOB horizontal line paints the visible range") {
            target(width = 4, height = 4).use { t ->
                Rasterizer.drawLine(t, -2, 1, 2, 1, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                painted(t) shouldBe setOf(0 to 1, 1 to 1, 2 to 1)
            }
        }
    })
