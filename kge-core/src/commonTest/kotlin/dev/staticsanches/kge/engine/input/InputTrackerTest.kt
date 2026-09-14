package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.engine.ViewportFit
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [InputTracker] latches olc's `HWButton` edges: `pressed` and `released` last
 * one frame, `held` spans from the press through the frame before the release.
 */
class InputTrackerTest :
    FunSpec({
        test("a rising edge reports pressed and held for one frame") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.setKeyDown(KeyboardKey.escape, true)
            tracker.latch(raw)

            tracker.state.key(KeyboardKey.escape).let {
                it.pressed shouldBe true
                it.held shouldBe true
                it.released shouldBe false
            }
        }

        test("a key that stays down only reports held") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.setKeyDown(KeyboardKey.escape, true)
            tracker.latch(raw)
            tracker.latch(raw)

            tracker.state.key(KeyboardKey.escape).let {
                it.pressed shouldBe false
                it.held shouldBe true
                it.released shouldBe false
            }
        }

        test("a falling edge reports released and clears held") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.setKeyDown(KeyboardKey.escape, true)
            tracker.latch(raw)
            raw.setKeyDown(KeyboardKey.escape, false)
            tracker.latch(raw)

            tracker.state.key(KeyboardKey.escape).let {
                it.pressed shouldBe false
                it.held shouldBe false
                it.released shouldBe true
            }
        }

        test("a second press reports a second edge") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.setKeyDown(KeyboardKey.escape, true)
            tracker.latch(raw)
            raw.setKeyDown(KeyboardKey.escape, false)
            tracker.latch(raw)
            raw.setKeyDown(KeyboardKey.escape, true)
            tracker.latch(raw)

            tracker.state.key(KeyboardKey.escape).pressed shouldBe true
        }

        test("mouse buttons follow the same edges") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.setMouseButtonDown(MouseButton.LEFT, true)
            tracker.latch(raw)
            tracker.state.mouseButton(MouseButton.LEFT).pressed shouldBe true
            tracker.state.mouseButton(MouseButton.LEFT).held shouldBe true

            raw.setMouseButtonDown(MouseButton.LEFT, false)
            tracker.latch(raw)
            tracker.state.mouseButton(MouseButton.LEFT).released shouldBe true
            tracker.state.mouseButton(MouseButton.LEFT).held shouldBe false
        }

        test("clearing the raw keys on focus loss synthesizes releases") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.setKeyDown(KeyboardKey.escape, true)
            tracker.latch(raw)

            raw.clearKeys()
            raw.focused = false
            tracker.latch(raw)

            tracker.state.key(KeyboardKey.escape).let {
                it.released shouldBe true
                it.held shouldBe false
                it.pressed shouldBe false
            }
            tracker.state.focused shouldBe false
        }

        test("the wheel delta is consumed once") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.wheelDelta = 5
            tracker.latch(raw)
            tracker.state.mouseWheel shouldBe 5
            raw.wheelDelta shouldBe 0

            tracker.latch(raw)
            tracker.state.mouseWheel shouldBe 0
        }

        test("modifiers and focus are published") {
            val tracker = InputTracker()
            val raw = RawInput()

            raw.modifiers = Modifiers.of(shift = true, capsLock = true)
            raw.focused = true
            tracker.latch(raw)

            tracker.state.modifiers.shift shouldBe true
            tracker.state.modifiers.capsLock shouldBe true
            tracker.state.focused shouldBe true
        }
    })

private fun InputTracker.latch(raw: RawInput) {
    latch(
        raw = raw,
        screenSize = Int2D(320, 240),
        fit = ViewportFit(Int2D.ZERO, Int2D(320, 240)),
        windowSize = Int2D(320, 240),
        framebufferSize = Int2D(320, 240),
    )
}
