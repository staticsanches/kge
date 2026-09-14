package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [RawInput] is the driver seam: a platform backend writes the raw state and
 * the engine reads and clears it.
 */
class RawInputTest :
    FunSpec({
        test("key bits are set, read and cleared") {
            val input = RawInput()

            input.isKeyDown(KeyboardKey.escape) shouldBe false
            input.setKeyDown(KeyboardKey.escape, true)
            input.isKeyDown(KeyboardKey.escape) shouldBe true
            input.isKeyDown(KeyboardKey.a) shouldBe false

            input.clearKeys()
            input.isKeyDown(KeyboardKey.escape) shouldBe false
        }

        test("mouse button bits are set and read") {
            val input = RawInput()

            input.isMouseButtonDown(MouseButton.LEFT) shouldBe false
            input.setMouseButtonDown(MouseButton.LEFT, true)
            input.isMouseButtonDown(MouseButton.LEFT) shouldBe true
            input.isMouseButtonDown(MouseButton.RIGHT) shouldBe false

            input.setMouseButtonDown(MouseButton.LEFT, false)
            input.isMouseButtonDown(MouseButton.LEFT) shouldBe false
        }

        test("the scalar fields hold what the driver wrote") {
            val input = RawInput()
            input.mousePosition shouldBe Int2D.ZERO
            input.wheelDelta shouldBe 0
            input.modifiers shouldBe Modifiers.of()
            input.focused shouldBe false

            input.mousePosition = Int2D(12, 34)
            input.wheelDelta = -2
            input.modifiers = Modifiers.of(shift = true)
            input.focused = true

            input.mousePosition shouldBe Int2D(12, 34)
            input.wheelDelta shouldBe -2
            input.modifiers.shift shouldBe true
            input.focused shouldBe true
        }
    })
