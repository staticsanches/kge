package dev.staticsanches.kge.rasterizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The pure [CircleOctantMask] set algebra: an 8-bit selection of the clockwise
 * octants, combined with [CircleOctantMask.or] and tested with
 * [CircleOctantMask.intersects].
 */
class CircleOctantMaskTest :
    FunSpec({
        val singles =
            listOf(
                CircleOctantMask.O1,
                CircleOctantMask.O2,
                CircleOctantMask.O3,
                CircleOctantMask.O4,
                CircleOctantMask.O5,
                CircleOctantMask.O6,
                CircleOctantMask.O7,
                CircleOctantMask.O8,
            )

        test("ALL intersects every single octant") {
            for (single in singles) {
                CircleOctantMask.ALL.intersects(single) shouldBe true
            }
        }

        test("NONE intersects no octant") {
            for (single in singles) {
                CircleOctantMask.NONE.intersects(single) shouldBe false
            }
            CircleOctantMask.NONE.intersects(CircleOctantMask.ALL) shouldBe false
        }

        test("or intersects exactly the union of the operands") {
            val union = CircleOctantMask.O1 or CircleOctantMask.O2
            for (single in singles) {
                union.intersects(single) shouldBe (single == CircleOctantMask.O1 || single == CircleOctantMask.O2)
            }
            union.intersects(CircleOctantMask.O1 or CircleOctantMask.O2) shouldBe true
        }

        test("NONE or a single octant equals that octant") {
            (CircleOctantMask.NONE or CircleOctantMask.O3) shouldBe CircleOctantMask.O3
        }

        test("ALL equals the union of every single octant") {
            val union = singles.reduce { acc, single -> acc or single }
            union shouldBe CircleOctantMask.ALL
        }
    })
