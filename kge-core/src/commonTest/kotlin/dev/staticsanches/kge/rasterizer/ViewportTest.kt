package dev.staticsanches.kge.rasterizer

import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The [Viewport] hierarchy and its `contains` predicate: lower bound inclusive,
 * upper bound exclusive; a missing bound on a partial variant constrains no axis.
 */
class ViewportTest :
    FunSpec({
        val bounded =
            object : Viewport.Bounded {
                override val lowerBoundInclusive: Int2D = Int2D(1, 2)
                override val upperBoundExclusive: Int2D = Int2D(4, 5)
            }
        val lowerBounded =
            object : Viewport.LowerBounded {
                override val lowerBoundInclusive: Int2D = Int2D(1, 2)
            }
        val upperBounded =
            object : Viewport.UpperBounded {
                override val upperBoundExclusive: Int2D = Int2D(4, 5)
            }

        test("Unbounded contains every point") {
            Viewport.Unbounded.contains(-1000, 1000) shouldBe true
            Viewport.Unbounded.contains(0, 0) shouldBe true
        }

        test("Bounded checks the inclusive lower and exclusive upper bounds") {
            bounded.contains(1, 2) shouldBe true
            bounded.contains(3, 4) shouldBe true
            bounded.contains(4, 5) shouldBe false
            bounded.contains(0, 2) shouldBe false
            bounded.contains(1, 5) shouldBe false
        }

        test("LowerBounded checks only the lower bounds") {
            lowerBounded.contains(1, 2) shouldBe true
            lowerBounded.contains(0, 0) shouldBe false
            lowerBounded.contains(1000, 1000) shouldBe true
        }

        test("UpperBounded checks only the upper bounds") {
            upperBounded.contains(0, 0) shouldBe true
            upperBounded.contains(4, 5) shouldBe false
            upperBounded.contains(-1000, -1000) shouldBe true
        }

        test("contains(Int2D) unpacks to contains(x, y)") {
            for (viewport in listOf(bounded, lowerBounded, upperBounded, Viewport.Unbounded)) {
                for (x in -2..6) {
                    for (y in -2..6) {
                        viewport.contains(Int2D(x, y)) shouldBe viewport.contains(x, y)
                    }
                }
            }
        }
    })
