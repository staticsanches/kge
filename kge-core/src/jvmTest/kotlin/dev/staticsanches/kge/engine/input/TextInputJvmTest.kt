package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.engine.applyCharacter
import dev.staticsanches.kge.engine.applyKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.glfw.GLFW

/** The GLFW backend feeds the raw text-input queue: a character once, an edit on press and repeat. */
class TextInputJvmTest :
    FunSpec({
        test("a GLFW character callback enqueues its code point") {
            val raw = RawInput()

            raw.applyCharacter(0x1F600)

            raw.drainTextInput() shouldBe listOf(TextInputEvent.Character(0x1F600))
        }

        test("a GLFW press and a GLFW repeat each enqueue the edit; a release does not") {
            val raw = RawInput()

            raw.applyKey(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_PRESS, 0)
            raw.applyKey(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_REPEAT, 0)
            raw.applyKey(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_RELEASE, 0)
            raw.applyKey(GLFW.GLFW_KEY_A, GLFW.GLFW_PRESS, 0)
            raw.applyKey(GLFW.GLFW_KEY_A, GLFW.GLFW_RELEASE, 0)

            raw.drainTextInput() shouldBe listOf(TextInputEvent.Edit.LEFT, TextInputEvent.Edit.LEFT)
        }
    })
