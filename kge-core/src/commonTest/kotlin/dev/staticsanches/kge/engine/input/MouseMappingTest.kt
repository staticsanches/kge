package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.engine.ViewportFit
import dev.staticsanches.kge.engine.fitViewport
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The pointer is mapped through the letterbox to screen pixels, matching olc's
 * `olc_UpdateMouse`: the window position is scaled to the framebuffer, offset by
 * the viewport and rescaled to the screen, then clamped.
 */
class MouseMappingTest :
    FunSpec({
        test("an unletterboxed window maps proportionally") {
            map(
                mouse = Int2D(200, 150),
                screenSize = Int2D(320, 240),
                fit = ViewportFit(Int2D.ZERO, Int2D(400, 300)),
                windowSize = Int2D(400, 300),
                framebufferSize = Int2D(400, 300),
            ) shouldBe Int2D(160, 120)
        }

        test("a letterboxed window subtracts the viewport offset") {
            map(
                mouse = Int2D(200, 250),
                screenSize = Int2D(320, 240),
                fit = ViewportFit(Int2D(0, 50), Int2D(400, 300)),
                windowSize = Int2D(400, 400),
                framebufferSize = Int2D(400, 400),
            ) shouldBe Int2D(160, 160)
        }

        test("positions outside the viewport clamp to the screen") {
            val screenSize = Int2D(320, 240)
            val fit = ViewportFit(Int2D(0, 50), Int2D(400, 300))

            map(Int2D(0, 0), screenSize, fit, Int2D(400, 400), Int2D(400, 400)) shouldBe Int2D(0, 0)
            map(Int2D(400, 400), screenSize, fit, Int2D(400, 400), Int2D(400, 400)) shouldBe Int2D(319, 239)
        }

        test("a HiDPI window scales points to framebuffer pixels first") {
            map(
                mouse = Int2D(100, 75),
                screenSize = Int2D(320, 240),
                fit = ViewportFit(Int2D.ZERO, Int2D(400, 300)),
                windowSize = Int2D(200, 150),
                framebufferSize = Int2D(400, 300),
            ) shouldBe Int2D(160, 120)
        }

        test("a degenerate window or viewport maps to the origin") {
            map(
                mouse = Int2D(100, 75),
                screenSize = Int2D(320, 240),
                fit = ViewportFit(Int2D.ZERO, Int2D.ZERO),
                windowSize = Int2D(200, 150),
                framebufferSize = Int2D(400, 300),
            ) shouldBe Int2D.ZERO

            map(
                mouse = Int2D(100, 75),
                screenSize = Int2D(320, 240),
                fit = ViewportFit(Int2D.ZERO, Int2D(400, 300)),
                windowSize = Int2D.ZERO,
                framebufferSize = Int2D(400, 300),
            ) shouldBe Int2D.ZERO
        }

        test("the mapping agrees with the engine letterbox fit") {
            val fit =
                fitViewport(
                    screenSize = Int2D(320, 240),
                    pixelSize = Int2D(1, 1),
                    framebufferSize = Int2D(400, 400),
                    cohesion = false,
                )
            fit shouldBe ViewportFit(Int2D(0, 50), Int2D(400, 300))

            map(Int2D(200, 250), Int2D(320, 240), fit, Int2D(400, 400), Int2D(400, 400)) shouldBe Int2D(160, 160)
        }
    })

private fun map(
    mouse: Int2D,
    screenSize: Int2D,
    fit: ViewportFit,
    windowSize: Int2D,
    framebufferSize: Int2D,
): Int2D = mapMouseToScreen(mouse, screenSize, fit, windowSize, framebufferSize)
