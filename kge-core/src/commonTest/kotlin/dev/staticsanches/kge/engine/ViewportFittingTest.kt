package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ViewportFittingTest :
    FunSpec({
        test("a 16:9 desired letterboxes top and bottom in a taller framebuffer") {
            val fit =
                fitViewport(
                    screenSize = Int2D(320, 180),
                    pixelSize = Int2D(1, 1),
                    framebufferSize = Int2D(640, 480),
                    cohesion = false,
                )

            fit.size shouldBe Int2D(640, 360)
            fit.position shouldBe Int2D(0, 60)
        }

        test("a wider framebuffer pillarboxes left and right") {
            val fit =
                fitViewport(
                    screenSize = Int2D(320, 180),
                    pixelSize = Int2D(1, 1),
                    framebufferSize = Int2D(800, 360),
                    cohesion = false,
                )

            fit.size shouldBe Int2D(640, 360)
            fit.position shouldBe Int2D(80, 0)
        }

        test("the art zoom scales the desired size before fitting") {
            val fit =
                fitViewport(
                    screenSize = Int2D(160, 120),
                    pixelSize = Int2D(2, 2),
                    framebufferSize = Int2D(1000, 1000),
                    cohesion = false,
                )

            fit.size shouldBe Int2D(1000, 750)
            fit.position shouldBe Int2D(0, 125)
        }

        test("a fractional fit keeps the exact centered pixel values") {
            val fit =
                fitViewport(
                    screenSize = Int2D(320, 240),
                    pixelSize = Int2D(1, 1),
                    framebufferSize = Int2D(1000, 1000),
                    cohesion = false,
                )

            fit.size shouldBe Int2D(1000, 750)
            fit.position shouldBe Int2D(0, 125)
        }

        test("cohesion snaps the scale down to a uniform integer") {
            val fit =
                fitViewport(
                    screenSize = Int2D(320, 240),
                    pixelSize = Int2D(1, 1),
                    framebufferSize = Int2D(1000, 1000),
                    cohesion = true,
                )

            fit.size shouldBe Int2D(960, 720)
            fit.position shouldBe Int2D(20, 140)
        }

        test("cohesion clamps to the framebuffer when the integer scale overflows") {
            val fit =
                fitViewport(
                    screenSize = Int2D(320, 240),
                    pixelSize = Int2D(1, 1),
                    framebufferSize = Int2D(100, 100),
                    cohesion = true,
                )

            fit.size shouldBe Int2D(100, 100)
            fit.position shouldBe Int2D(0, 0)
        }

        test("a non-positive framebuffer axis yields a zero-size fit") {
            for (framebufferSize in listOf(Int2D(0, 0), Int2D(800, 0), Int2D(0, 480), Int2D(-1, 100))) {
                val fit =
                    fitViewport(
                        screenSize = Int2D(320, 240),
                        pixelSize = Int2D(1, 1),
                        framebufferSize = framebufferSize,
                        cohesion = false,
                    )

                fit shouldBe ViewportFit(Int2D.ZERO, Int2D.ZERO)
            }
        }
    })
