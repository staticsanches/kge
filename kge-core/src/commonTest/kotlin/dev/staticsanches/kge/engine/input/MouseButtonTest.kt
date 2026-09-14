package dev.staticsanches.kge.engine.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The mouse button set and its olc order. */
class MouseButtonTest :
    FunSpec({
        test("the button set is left, right and middle") {
            MouseButton.entries shouldBe listOf(MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE)
        }
    })
