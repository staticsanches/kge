package dev.staticsanches.kge.math.vector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class Int2DTest :
    FunSpec({
        context("representation") {
            test("constructs with readable x and y") {
                val p = Int2D(3, -4)
                p.x shouldBe 3
                p.y shouldBe -4
            }

            test("equal values are equal; differing values are not") {
                Int2D(1, 2) shouldBe Int2D(1, 2)
                Int2D(1, 2) shouldNotBe Int2D(1, 3)
            }

            test("destructures into x and y") {
                val (a, b) = Int2D(3, 4)
                a shouldBe 3
                b shouldBe 4
            }

            test("toString renders (x, y) with a space after the comma") {
                Int2D(1, -2).toString() shouldBe "(1, -2)"
                Int2D(7, 8).toString() shouldBe "(7, 8)"
            }

            test("ZERO is the origin") {
                Int2D.ZERO shouldBe Int2D(0, 0)
            }
        }

        context("arithmetic") {
            test("plus is componentwise") {
                Int2D(3, 5) + Int2D(2, -1) shouldBe Int2D(5, 4)
            }

            test("minus is componentwise") {
                Int2D(3, 5) - Int2D(2, -1) shouldBe Int2D(1, 6)
            }

            test("times is componentwise") {
                Int2D(3, 5) * Int2D(2, 3) shouldBe Int2D(6, 15)
            }

            test("div is componentwise and truncates like Int / Int") {
                Int2D(7, 9) / Int2D(2, 3) shouldBe Int2D(3, 3)
            }

            test("times by a scalar scales both components") {
                Int2D(3, 5) * 2 shouldBe Int2D(6, 10)
            }

            test("div by a scalar divides both components toward zero") {
                Int2D(7, -7) / 2 shouldBe Int2D(3, -3)
            }

            test("unaryMinus negates both components") {
                -Int2D(1, -2) shouldBe Int2D(-1, 2)
            }

            test("componentwise div by a zero component throws") {
                shouldThrow<ArithmeticException> { Int2D(4, 2) / Int2D(0, 1) }
                shouldThrow<ArithmeticException> { Int2D(4, 2) / Int2D(1, 0) }
            }

            test("scalar div by zero throws") {
                shouldThrow<ArithmeticException> { Int2D(4, 2) / 0 }
            }
        }

        context("reference math") {
            test("perp rotates a quarter turn counter-clockwise") {
                Int2D(3, 4).perp() shouldBe Int2D(-4, 3)
            }

            test("dot is the component product sum") {
                Int2D(3, 4).dot(Int2D(2, 5)) shouldBe 26
            }

            test("cross is x * oy - y * ox") {
                Int2D(3, 4).cross(Int2D(2, 5)) shouldBe 7
            }

            test("mag2 is the squared length") {
                Int2D(3, 4).mag2() shouldBe 25
            }

            test("mag is the double-precision length of known squares") {
                Int2D(3, 4).mag() shouldBe 5.0
            }

            test("area is the component product") {
                Int2D(3, 4).area() shouldBe 12
            }

            test("min and max pick the componentwise extremes") {
                Int2D(1, 5).min(Int2D(3, 2)) shouldBe Int2D(1, 2)
                Int2D(1, 5).max(Int2D(3, 2)) shouldBe Int2D(3, 5)
            }

            test("clamp bounds each component to the low/high rectangle") {
                Int2D(4, 0).clamp(Int2D(1, 1), Int2D(3, 3)) shouldBe Int2D(3, 1)
                Int2D(-1, 3).clamp(Int2D(0, 0), Int2D(2, 2)) shouldBe Int2D(0, 2)
            }
        }

        context("conversion") {
            test("toFloat widens both components") {
                Int2D(3, 4).toFloat() shouldBe Float2D(3f, 4f)
            }
        }
    })
