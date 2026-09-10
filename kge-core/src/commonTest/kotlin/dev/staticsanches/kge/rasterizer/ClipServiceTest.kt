package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.service.ClipService
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The default [ClipService] reproduces olc v2.30 `ClipLineToDrawTarget`:
 * integer Cohen–Sutherland with a boundary inclusive at the viewport's
 * exclusive upper bound, truncating division, and a trivial reject on
 * `s1 & s2`.
 */
class ClipServiceTest :
    FunSpec({
        fun bounded(
            lower: Int2D,
            upper: Int2D,
        ): Viewport.Bounded =
            object : Viewport.Bounded {
                override val lowerBoundInclusive: Int2D = lower
                override val upperBoundExclusive: Int2D = upper
            }

        fun lowerBounded(lower: Int2D): Viewport.LowerBounded =
            object : Viewport.LowerBounded {
                override val lowerBoundInclusive: Int2D = lower
            }

        fun upperBounded(upper: Int2D): Viewport.UpperBounded =
            object : Viewport.UpperBounded {
                override val upperBoundExclusive: Int2D = upper
            }

        context("Bounded — olc ClipLineToDrawTarget parity") {
            val viewport = bounded(Int2D(0, 0), Int2D(4, 4))

            test("a fully contained segment is unchanged") {
                ClipService.clipLineTo(viewport, Int2D(1, 1), Int2D(3, 2)) shouldBe (Int2D(1, 1) to Int2D(3, 2))
            }

            test("a segment fully left and below is trivially rejected") {
                ClipService.clipLineTo(viewport, Int2D(-1, -1), Int2D(-2, -2)) shouldBe null
            }

            test("a segment fully right and above is trivially rejected") {
                ClipService.clipLineTo(viewport, Int2D(5, 5), Int2D(6, 6)) shouldBe null
            }

            test("a left-crossing segment clips below first, then left") {
                ClipService.clipLineTo(viewport, Int2D(-3, -1), Int2D(3, 2)) shouldBe (Int2D(0, 0) to Int2D(3, 2))
            }

            test("a top-crossing segment keeps the point exactly on the upper bound") {
                ClipService.clipLineTo(viewport, Int2D(1, 6), Int2D(1, 2)) shouldBe (Int2D(1, 4) to Int2D(1, 2))
            }

            test("a top-crossing segment that then crosses the left edge") {
                ClipService.clipLineTo(viewport, Int2D(3, 5), Int2D(-1, 1)) shouldBe (Int2D(2, 4) to Int2D(0, 2))
            }

            test("intersection division truncates toward zero with a negative numerator") {
                ClipService.clipLineTo(viewport, Int2D(-1, 3), Int2D(2, 1)) shouldBe (Int2D(0, 3) to Int2D(2, 1))
            }
        }

        context("Unbounded") {
            test("never rejects and returns the segment unchanged") {
                val start = Int2D(-5, 9)
                val end = Int2D(100, -3)
                ClipService.clipLineTo(Viewport.Unbounded, start, end) shouldBe (start to end)
            }
        }

        context("LowerBounded — clips only the lower axes") {
            val viewport = lowerBounded(Int2D(2, 2))

            test("clips a segment entering from the left") {
                ClipService.clipLineTo(viewport, Int2D(-1, 3), Int2D(5, 3)) shouldBe (Int2D(2, 3) to Int2D(5, 3))
            }

            test("rejects a segment fully below and left") {
                ClipService.clipLineTo(viewport, Int2D(-1, -1), Int2D(-2, -2)) shouldBe null
            }
        }

        context("UpperBounded — clips only the upper axes") {
            val viewport = upperBounded(Int2D(4, 4))

            test("clips a segment exiting to the right") {
                ClipService.clipLineTo(viewport, Int2D(1, 1), Int2D(6, 1)) shouldBe (Int2D(1, 1) to Int2D(4, 1))
            }

            test("rejects a segment fully above and right") {
                ClipService.clipLineTo(viewport, Int2D(5, 5), Int2D(6, 6)) shouldBe null
            }
        }

        context("the seam is observable") {
            fun target(): Sprite =
                SpriteService
                    .create(4, 4, Pixmap.SampleMode.NORMAL, null)
                    .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

            fun painted(t: Sprite): Set<Pair<Int, Int>> =
                (0 until t.height)
                    .flatMap { y -> (0 until t.width).map { x -> x to y } }
                    .filter { (x, y) -> t.get(x, y) != Colors.TRANSPARENT }
                    .toSet()

            test("an active override is observed by Rasterizer.clipLineTo and drawLine, and resetAll restores it") {
                val original = ClipService.original
                ClipService.override(
                    object : ClipService by original {
                        override fun clipLineTo(
                            viewport: Viewport,
                            start: Int2D,
                            end: Int2D,
                        ): Pair<Int2D, Int2D>? = if (start.x < 0) null else original.clipLineTo(viewport, start, end)
                    },
                )

                Rasterizer.clipLineTo(Viewport.Unbounded, Int2D(-3, -1), Int2D(3, 2)) shouldBe null
                val clipped = Rasterizer.clipLineTo(Viewport.Unbounded, Int2D(0, 0), Int2D(3, 2))
                clipped shouldBe (Int2D(0, 0) to Int2D(3, 2))

                target().use { t ->
                    Rasterizer.drawLine(t, -3, -1, 3, 2, Colors.RED, Pixel.Mode.Normal)
                    painted(t) shouldBe emptySet()
                }
                target().use { t ->
                    Rasterizer.drawLine(t, 0, 0, 3, 2, Colors.RED, Pixel.Mode.Normal)
                    painted(t) shouldBe setOf(0 to 0, 1 to 1, 2 to 1, 3 to 2)
                }

                KGEOverridable.Proxy.resetAll()

                target().use { t ->
                    Rasterizer.drawLine(t, -3, -1, 3, 2, Colors.RED, Pixel.Mode.Normal)
                    painted(t) shouldBe setOf(0 to 0, 1 to 1, 2 to 1, 3 to 2)
                }
            }
        }
    })
