package dev.staticsanches.kge.engine.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The per-frame edge triple of a key or mouse button. */
class ButtonStateTest :
    FunSpec({
        test("an unset button reports no edge") {
            val state = ButtonState.of(pressed = false, released = false, held = false)
            state.pressed shouldBe false
            state.released shouldBe false
            state.held shouldBe false
        }

        test("each flag reads independently") {
            ButtonState.of(pressed = true, released = false, held = false).pressed shouldBe true
            ButtonState.of(pressed = true, released = false, held = false).released shouldBe false
            ButtonState.of(pressed = false, released = true, held = false).released shouldBe true
            ButtonState.of(pressed = false, released = false, held = true).held shouldBe true
        }

        test("pressed and held co-occur on a rising edge") {
            val state = ButtonState.of(pressed = true, released = false, held = true)
            state.pressed shouldBe true
            state.held shouldBe true
            state.released shouldBe false
        }
    })
