package dev.staticsanches.kge.math.vector

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class Float2DTest :
    FunSpec({
        fun expectClose(
            actual: Float2D,
            expected: Float2D,
            tolerance: Float = 1e-5f,
        ) {
            withClue("x: expected ~${expected.x}, got ${actual.x}") {
                abs(actual.x - expected.x) shouldBeLessThanOrEqualTo tolerance
            }
            withClue("y: expected ~${expected.y}, got ${actual.y}") {
                abs(actual.y - expected.y) shouldBeLessThanOrEqualTo tolerance
            }
        }

        context("representation") {
            test("constructs with readable x and y") {
                val p = Float2D(1.5f, -2.25f)
                p.x shouldBe 1.5f
                p.y shouldBe -2.25f
            }

            test("equal exact representables are equal; differing are not") {
                Float2D(1.5f, -2.25f) shouldBe Float2D(1.5f, -2.25f)
                Float2D(1.5f, -2.25f) shouldNotBe Float2D(1.5f, 2.25f)
            }

            test("destructures into x and y") {
                val (a, b) = Float2D(0.5f, 2f)
                a shouldBe 0.5f
                b shouldBe 2f
            }

            test("toString renders (x, y) from non-integral components") {
                Float2D(1.5f, -2.25f).toString() shouldBe "(1.5, -2.25)"
            }
        }

        context("arithmetic") {
            test("plus is componentwise") {
                Float2D(1.5f, 2f) + Float2D(0.5f, 1f) shouldBe Float2D(2f, 3f)
            }

            test("minus is componentwise") {
                Float2D(2f, 3f) - Float2D(0.5f, 1f) shouldBe Float2D(1.5f, 2f)
            }

            test("times is componentwise") {
                Float2D(1.5f, 2f) * Float2D(2f, 3f) shouldBe Float2D(3f, 6f)
            }

            test("div is componentwise") {
                Float2D(3f, 6f) / Float2D(2f, 3f) shouldBe Float2D(1.5f, 2f)
            }

            test("times by a scalar scales both components") {
                Float2D(1.5f, 2f) * 2f shouldBe Float2D(3f, 4f)
            }

            test("div by a scalar divides both components") {
                Float2D(3f, 4f) / 2f shouldBe Float2D(1.5f, 2f)
            }

            test("div by an Int2D divides by each component") {
                Float2D(1f, 2f) / Int2D(2, 4) shouldBe Float2D(0.5f, 0.5f)
            }

            test("unaryMinus negates both components") {
                -Float2D(1.5f, -2f) shouldBe Float2D(-1.5f, 2f)
            }

            test("div by a zero float component yields an infinite component") {
                val r = Float2D(1f, 1f) / Float2D(0f, 1f)
                r.x.isInfinite() shouldBe true
                r.y shouldBe 1f
            }
        }

        context("reference math") {
            test("mag2 is the squared length") {
                Float2D(3f, 4f).mag2() shouldBe 25f
            }

            test("mag is the length") {
                Float2D(3f, 4f).mag() shouldBe 5f
            }

            test("perp rotates a quarter turn counter-clockwise") {
                Float2D(3f, 4f).perp() shouldBe Float2D(-4f, 3f)
            }

            test("dot is the component product sum") {
                Float2D(3f, 4f).dot(Float2D(2f, 5f)) shouldBe 26f
            }

            test("cross is x * oy - y * ox") {
                Float2D(3f, 4f).cross(Float2D(2f, 5f)) shouldBe 7f
            }

            test("area is the component product") {
                Float2D(3f, 4f).area() shouldBe 12f
            }

            test("norm of a 3-4-5 vector is the unit components") {
                // Float math on JS is not float32-rounded, so a norm is
                // tolerance-guarded, never exact.
                expectClose(Float2D(3f, 4f).norm(), Float2D(0.6f, 0.8f))
            }

            test("norm of the zero vector yields NaN components") {
                val n = Float2D(0f, 0f).norm()
                n.x.isNaN() shouldBe true
                n.y.isNaN() shouldBe true
            }

            test("floor and ceil round each component") {
                Float2D(1.2f, -1.8f).floor() shouldBe Float2D(1f, -2f)
                Float2D(1.2f, -1.8f).ceil() shouldBe Float2D(2f, -1f)
            }

            test("min and max pick the componentwise extremes") {
                Float2D(1f, 5f).min(Float2D(3f, 2f)) shouldBe Float2D(1f, 2f)
                Float2D(1f, 5f).max(Float2D(3f, 2f)) shouldBe Float2D(3f, 5f)
            }

            test("clamp bounds each component to the low/high rectangle") {
                Float2D(4f, 0f).clamp(Float2D(1f, 1f), Float2D(3f, 3f)) shouldBe Float2D(3f, 1f)
                Float2D(-1f, 3f).clamp(Float2D(0f, 0f), Float2D(2f, 2f)) shouldBe Float2D(0f, 2f)
            }
        }

        context("transcendental pins") {
            test("lerp interpolates by t") {
                val v = Float2D(2f, 0f).lerp(Float2D(0f, 2f), 0.25f)
                expectClose(v, Float2D(1.5f, 0.5f))
            }

            test("polar maps cartesian to radius and angle") {
                val v = Float2D(1f, 1f).polar()
                expectClose(v, Float2D(sqrt(2f), PI.toFloat() / 4f))
            }

            test("cart maps radius and angle to cartesian") {
                val v = Float2D(2f, 0f).cart()
                expectClose(v, Float2D(2f, 0f))
            }

            test("reflect mirrors around the given normal") {
                Float2D(1f, -1f).reflect(Float2D(0f, 1f)) shouldBe Float2D(1f, 1f)
            }
        }

        context("conversion") {
            test("toInt truncates each component toward zero") {
                Float2D(1.9f, -1.9f).toInt() shouldBe Int2D(1, -1)
            }

            test("round-trips exact representables") {
                Float2D(3f, 4f).toInt() shouldBe Int2D(3, 4)
                Int2D(3, 4).toFloat() shouldBe Float2D(3f, 4f)
            }

            test("toString renders the non-integral edge with a space after the comma") {
                Float2D(0.5f, 1.5f).toString() shouldBe "(0.5, 1.5)"
            }
        }
    })
