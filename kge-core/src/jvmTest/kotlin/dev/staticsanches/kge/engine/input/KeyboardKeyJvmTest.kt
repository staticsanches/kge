package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.engine.applyFocus
import dev.staticsanches.kge.engine.applyKey
import dev.staticsanches.kge.engine.applyMouseButton
import dev.staticsanches.kge.engine.applyMouseMove
import dev.staticsanches.kge.engine.applyScroll
import dev.staticsanches.kge.engine.glfwModifiers
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.glfw.GLFW

/** The JVM GLFW code table and the adapter that drives the raw state from it. */
class KeyboardKeyJvmTest :
    FunSpec({
        test("the GLFW code table resolves to the native members") {
            KeyboardKey[GLFW.GLFW_KEY_A] shouldBe keyboardKey(KeyVocabulary.A)
            KeyboardKey[GLFW.GLFW_KEY_ESCAPE] shouldBe keyboardKey(KeyVocabulary.Escape)
            KeyboardKey[GLFW.GLFW_KEY_F5] shouldBe keyboardKey(KeyVocabulary.F5)
            KeyboardKey[GLFW.GLFW_KEY_SEMICOLON] shouldBe keyboardKey(KeyVocabulary.Oem1)
            KeyboardKey[GLFW.GLFW_KEY_KP_MULTIPLY] shouldBe keyboardKey(KeyVocabulary.NumpadMultiply)
        }

        test("the main and keypad Enter collapse to one key") {
            KeyboardKey[GLFW.GLFW_KEY_ENTER] shouldBe keyboardKey(KeyVocabulary.Enter)
            KeyboardKey[GLFW.GLFW_KEY_KP_ENTER] shouldBe keyboardKey(KeyVocabulary.Enter)
        }

        test("an unmapped code falls back to the last entry") {
            KeyboardKey[9999] shouldBe KeyboardKey.entries.last()
        }

        test("glfwModifiers translates the GLFW mask") {
            val modifiers = glfwModifiers(GLFW.GLFW_MOD_SHIFT or GLFW.GLFW_MOD_ALT or GLFW.GLFW_MOD_CAPS_LOCK)

            modifiers.shift shouldBe true
            modifiers.alt shouldBe true
            modifiers.capsLock shouldBe true
            modifiers.ctrl shouldBe false
        }

        test("a GLFW key event drives the raw state, repeat included") {
            val raw = RawInput()

            raw.applyKey(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS, GLFW.GLFW_MOD_SHIFT)
            raw.isKeyDown(keyboardKey(KeyVocabulary.Escape)) shouldBe true
            raw.modifiers.shift shouldBe true

            raw.applyKey(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_REPEAT, 0)
            raw.isKeyDown(keyboardKey(KeyVocabulary.Escape)) shouldBe true

            raw.applyKey(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_RELEASE, 0)
            raw.isKeyDown(keyboardKey(KeyVocabulary.Escape)) shouldBe false
        }

        test("a GLFW mouse button, cursor and scroll drive the raw state") {
            val raw = RawInput()

            raw.applyMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS)
            raw.isMouseButtonDown(MouseButton.LEFT) shouldBe true
            raw.applyMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE)
            raw.isMouseButtonDown(MouseButton.LEFT) shouldBe false

            raw.applyMouseMove(12.7, 34.1)
            raw.mousePosition shouldBe Int2D(12, 34)

            raw.applyScroll(2.0)
            raw.wheelDelta shouldBe 2
        }

        test("losing focus releases the held buttons") {
            val raw = RawInput()
            raw.applyKey(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS, 0)
            raw.applyMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS)

            raw.applyFocus(false)

            raw.focused shouldBe false
            raw.isKeyDown(keyboardKey(KeyVocabulary.Escape)) shouldBe false
            raw.isMouseButtonDown(MouseButton.LEFT) shouldBe false
        }
    })
