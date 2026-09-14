package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.Modifiers
import dev.staticsanches.kge.engine.input.MouseButton
import dev.staticsanches.kge.engine.input.RawInput
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import web.events.Event
import web.events.RESIZE
import web.mouse.AUXILIARY
import web.mouse.MAIN
import web.mouse.SECONDARY
import web.mouse.MouseButton as DomMouseButton
import web.window.window as eventWindow

/**
 * The web driver adapts DOM events onto the raw input: key codes resolve through
 * the common vocabulary, DOM mouse buttons map to the engine buttons, the wheel
 * sign matches olc, losing focus releases the held buttons, and the owned canvas
 * re-fits on resize.
 */
class WebDriverInputTest :
    FunSpec({
        test("the W3C code table resolves and falls back") {
            KeyboardKey["KeyA"] shouldBe KeyboardKey.KeyA
            KeyboardKey["NumpadEnter"] shouldBe KeyboardKey.Enter
            KeyboardKey["NoSuchCode"] shouldBe KeyboardKey.Unidentified
        }

        test("a key code resolves and the modifiers are published") {
            val raw = RawInput()

            raw.applyKey("Escape", Modifiers.of(shift = true), down = true)
            raw.isKeyDown(KeyboardKey["Escape"]) shouldBe true
            raw.modifiers.shift shouldBe true

            raw.applyKey("Escape", Modifiers.of(), down = false)
            raw.isKeyDown(KeyboardKey["Escape"]) shouldBe false
        }

        test("DOM mouse buttons map to the engine buttons") {
            val raw = RawInput()

            raw.applyMouseButton(DomMouseButton.MAIN, down = true)
            raw.isMouseButtonDown(MouseButton.LEFT) shouldBe true

            raw.applyMouseButton(DomMouseButton.AUXILIARY, down = true)
            raw.isMouseButtonDown(MouseButton.MIDDLE) shouldBe true

            raw.applyMouseButton(DomMouseButton.SECONDARY, down = true)
            raw.isMouseButtonDown(MouseButton.RIGHT) shouldBe true
        }

        test("a DOM cursor position is kept in window points") {
            val raw = RawInput()

            raw.applyMouseMove(12.6, 34.2)

            raw.mousePosition shouldBe Int2D(12, 34)
        }

        test("a DOM wheel delta is negated to the engine's positive-up") {
            val raw = RawInput()

            raw.applyScroll(3.0)

            raw.wheelDelta shouldBe -3
        }

        test("losing focus releases the held buttons") {
            val raw = RawInput()
            raw.applyKey("Escape", Modifiers.of(), down = true)
            raw.applyMouseButton(DomMouseButton.MAIN, down = true)

            raw.applyFocus(false)

            raw.focused shouldBe false
            raw.isKeyDown(KeyboardKey["Escape"]) shouldBe false
            raw.isMouseButtonDown(MouseButton.LEFT) shouldBe false
        }

        test("fitCanvasSize preserves the aspect ratio and leaves a degenerate window unchanged") {
            fitCanvasSize(Int2D(320, 240), Int2D(640, 640)) shouldBe Int2D(640, 480)
            fitCanvasSize(Int2D(320, 240), Int2D(160, 240)) shouldBe Int2D(160, 120)
            fitCanvasSize(Int2D(320, 240), Int2D(0, 0)) shouldBe Int2D(320, 240)
        }

        test("a resize event re-lays out the owned canvas preserving the aspect") {
            val driver = DriverService.create(WindowConfig(screenWidth = 320, screenHeight = 240))
            try {
                eventWindow.dispatchEvent(Event(Event.RESIZE))

                val expected = fitCanvasSize(Int2D(320, 240), Int2D(eventWindow.innerWidth, eventWindow.innerHeight))
                driver.windowSize() shouldBe expected
            } finally {
                driver.close()
            }
        }
    })
