@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.engine.input

import org.lwjgl.glfw.GLFW

actual enum class KeyboardKey(
    val glfwCode: Int,
) {
    // Printable keys
    KEY_SPACE(GLFW.GLFW_KEY_SPACE),
    KEY_APOSTROPHE(GLFW.GLFW_KEY_APOSTROPHE),
    KEY_COMMA(GLFW.GLFW_KEY_COMMA),
    KEY_MINUS(GLFW.GLFW_KEY_MINUS),
    KEY_PERIOD(GLFW.GLFW_KEY_PERIOD),
    KEY_SLASH(GLFW.GLFW_KEY_SLASH),
    KEY_0(GLFW.GLFW_KEY_0),
    KEY_1(GLFW.GLFW_KEY_1),
    KEY_2(GLFW.GLFW_KEY_2),
    KEY_3(GLFW.GLFW_KEY_3),
    KEY_4(GLFW.GLFW_KEY_4),
    KEY_5(GLFW.GLFW_KEY_5),
    KEY_6(GLFW.GLFW_KEY_6),
    KEY_7(GLFW.GLFW_KEY_7),
    KEY_8(GLFW.GLFW_KEY_8),
    KEY_9(GLFW.GLFW_KEY_9),
    KEY_SEMICOLON(GLFW.GLFW_KEY_SEMICOLON),
    KEY_EQUAL(GLFW.GLFW_KEY_EQUAL),
    KEY_A(GLFW.GLFW_KEY_A),
    KEY_B(GLFW.GLFW_KEY_B),
    KEY_C(GLFW.GLFW_KEY_C),
    KEY_D(GLFW.GLFW_KEY_D),
    KEY_E(GLFW.GLFW_KEY_E),
    KEY_F(GLFW.GLFW_KEY_F),
    KEY_G(GLFW.GLFW_KEY_G),
    KEY_H(GLFW.GLFW_KEY_H),
    KEY_I(GLFW.GLFW_KEY_I),
    KEY_J(GLFW.GLFW_KEY_J),
    KEY_K(GLFW.GLFW_KEY_K),
    KEY_L(GLFW.GLFW_KEY_L),
    KEY_M(GLFW.GLFW_KEY_M),
    KEY_N(GLFW.GLFW_KEY_N),
    KEY_O(GLFW.GLFW_KEY_O),
    KEY_P(GLFW.GLFW_KEY_P),
    KEY_Q(GLFW.GLFW_KEY_Q),
    KEY_R(GLFW.GLFW_KEY_R),
    KEY_S(GLFW.GLFW_KEY_S),
    KEY_T(GLFW.GLFW_KEY_T),
    KEY_U(GLFW.GLFW_KEY_U),
    KEY_V(GLFW.GLFW_KEY_V),
    KEY_W(GLFW.GLFW_KEY_W),
    KEY_X(GLFW.GLFW_KEY_X),
    KEY_Y(GLFW.GLFW_KEY_Y),
    KEY_Z(GLFW.GLFW_KEY_Z),
    KEY_LEFT_BRACKET(GLFW.GLFW_KEY_LEFT_BRACKET),
    KEY_BACKSLASH(GLFW.GLFW_KEY_BACKSLASH),
    KEY_RIGHT_BRACKET(GLFW.GLFW_KEY_RIGHT_BRACKET),
    KEY_GRAVE_ACCENT(GLFW.GLFW_KEY_GRAVE_ACCENT),
    KEY_WORLD_1(GLFW.GLFW_KEY_WORLD_1),
    KEY_WORLD_2(GLFW.GLFW_KEY_WORLD_2),

    // Function keys
    KEY_ESCAPE(GLFW.GLFW_KEY_ESCAPE),
    KEY_ENTER(GLFW.GLFW_KEY_ENTER),
    KEY_TAB(GLFW.GLFW_KEY_TAB),
    KEY_BACKSPACE(GLFW.GLFW_KEY_BACKSPACE),
    KEY_INSERT(GLFW.GLFW_KEY_INSERT),
    KEY_DELETE(GLFW.GLFW_KEY_DELETE),
    KEY_RIGHT(GLFW.GLFW_KEY_RIGHT),
    KEY_LEFT(GLFW.GLFW_KEY_LEFT),
    KEY_DOWN(GLFW.GLFW_KEY_DOWN),
    KEY_UP(GLFW.GLFW_KEY_UP),
    KEY_PAGE_UP(GLFW.GLFW_KEY_PAGE_UP),
    KEY_PAGE_DOWN(GLFW.GLFW_KEY_PAGE_DOWN),
    KEY_HOME(GLFW.GLFW_KEY_HOME),
    KEY_END(GLFW.GLFW_KEY_END),
    KEY_CAPS_LOCK(GLFW.GLFW_KEY_CAPS_LOCK),
    KEY_SCROLL_LOCK(GLFW.GLFW_KEY_SCROLL_LOCK),
    KEY_NUM_LOCK(GLFW.GLFW_KEY_NUM_LOCK),
    KEY_PRINT_SCREEN(GLFW.GLFW_KEY_PRINT_SCREEN),
    KEY_PAUSE(GLFW.GLFW_KEY_PAUSE),
    KEY_F1(GLFW.GLFW_KEY_F1),
    KEY_F2(GLFW.GLFW_KEY_F2),
    KEY_F3(GLFW.GLFW_KEY_F3),
    KEY_F4(GLFW.GLFW_KEY_F4),
    KEY_F5(GLFW.GLFW_KEY_F5),
    KEY_F6(GLFW.GLFW_KEY_F6),
    KEY_F7(GLFW.GLFW_KEY_F7),
    KEY_F8(GLFW.GLFW_KEY_F8),
    KEY_F9(GLFW.GLFW_KEY_F9),
    KEY_F10(GLFW.GLFW_KEY_F10),
    KEY_F11(GLFW.GLFW_KEY_F11),
    KEY_F12(GLFW.GLFW_KEY_F12),
    KEY_F13(GLFW.GLFW_KEY_F13),
    KEY_F14(GLFW.GLFW_KEY_F14),
    KEY_F15(GLFW.GLFW_KEY_F15),
    KEY_F16(GLFW.GLFW_KEY_F16),
    KEY_F17(GLFW.GLFW_KEY_F17),
    KEY_F18(GLFW.GLFW_KEY_F18),
    KEY_F19(GLFW.GLFW_KEY_F19),
    KEY_F20(GLFW.GLFW_KEY_F20),
    KEY_F21(GLFW.GLFW_KEY_F21),
    KEY_F22(GLFW.GLFW_KEY_F22),
    KEY_F23(GLFW.GLFW_KEY_F23),
    KEY_F24(GLFW.GLFW_KEY_F24),
    KEY_F25(GLFW.GLFW_KEY_F25),
    KEY_KP_0(GLFW.GLFW_KEY_KP_0),
    KEY_KP_1(GLFW.GLFW_KEY_KP_1),
    KEY_KP_2(GLFW.GLFW_KEY_KP_2),
    KEY_KP_3(GLFW.GLFW_KEY_KP_3),
    KEY_KP_4(GLFW.GLFW_KEY_KP_4),
    KEY_KP_5(GLFW.GLFW_KEY_KP_5),
    KEY_KP_6(GLFW.GLFW_KEY_KP_6),
    KEY_KP_7(GLFW.GLFW_KEY_KP_7),
    KEY_KP_8(GLFW.GLFW_KEY_KP_8),
    KEY_KP_9(GLFW.GLFW_KEY_KP_9),
    KEY_KP_DECIMAL(GLFW.GLFW_KEY_KP_DECIMAL),
    KEY_KP_DIVIDE(GLFW.GLFW_KEY_KP_DIVIDE),
    KEY_KP_MULTIPLY(GLFW.GLFW_KEY_KP_MULTIPLY),
    KEY_KP_SUBTRACT(GLFW.GLFW_KEY_KP_SUBTRACT),
    KEY_KP_ADD(GLFW.GLFW_KEY_KP_ADD),
    KEY_KP_ENTER(GLFW.GLFW_KEY_KP_ENTER),
    KEY_KP_EQUAL(GLFW.GLFW_KEY_KP_EQUAL),
    KEY_LEFT_SHIFT(GLFW.GLFW_KEY_LEFT_SHIFT),
    KEY_LEFT_CONTROL(GLFW.GLFW_KEY_LEFT_CONTROL),
    KEY_LEFT_ALT(GLFW.GLFW_KEY_LEFT_ALT),
    KEY_LEFT_SUPER(GLFW.GLFW_KEY_LEFT_SUPER),
    KEY_RIGHT_SHIFT(GLFW.GLFW_KEY_RIGHT_SHIFT),
    KEY_RIGHT_CONTROL(GLFW.GLFW_KEY_RIGHT_CONTROL),
    KEY_RIGHT_ALT(GLFW.GLFW_KEY_RIGHT_ALT),
    KEY_RIGHT_SUPER(GLFW.GLFW_KEY_RIGHT_SUPER),
    KEY_MENU(GLFW.GLFW_KEY_MENU),

    // Fallback: must stay the last entry — the vocabulary maps away from it.
    KEY_UNKNOWN(-1),
    ;

    actual companion object {
        private val byGlfwCode: Map<Int, KeyboardKey> = entries.associateBy { it.glfwCode }

        /**
         * The native key for a GLFW key code; the main and keypad Enter both
         * resolve to [KEY_ENTER] (olc collapse), and an unmapped code to
         * [KEY_UNKNOWN].
         */
        operator fun get(glfwCode: Int): KeyboardKey =
            if (glfwCode == GLFW.GLFW_KEY_KP_ENTER) KEY_ENTER else byGlfwCode[glfwCode] ?: KEY_UNKNOWN
    }
}

/**
 * Left-hand modifiers back [KeyVocabulary.Shift] and [KeyVocabulary.Ctrl]; the
 * right-hand variants are platform-only and reach common code through the
 * modifier snapshot.
 */
internal actual fun keyboardKey(vocabulary: KeyVocabulary): KeyboardKey =
    when (vocabulary) {
        KeyVocabulary.A -> KeyboardKey.KEY_A
        KeyVocabulary.B -> KeyboardKey.KEY_B
        KeyVocabulary.C -> KeyboardKey.KEY_C
        KeyVocabulary.D -> KeyboardKey.KEY_D
        KeyVocabulary.E -> KeyboardKey.KEY_E
        KeyVocabulary.F -> KeyboardKey.KEY_F
        KeyVocabulary.G -> KeyboardKey.KEY_G
        KeyVocabulary.H -> KeyboardKey.KEY_H
        KeyVocabulary.I -> KeyboardKey.KEY_I
        KeyVocabulary.J -> KeyboardKey.KEY_J
        KeyVocabulary.K -> KeyboardKey.KEY_K
        KeyVocabulary.L -> KeyboardKey.KEY_L
        KeyVocabulary.M -> KeyboardKey.KEY_M
        KeyVocabulary.N -> KeyboardKey.KEY_N
        KeyVocabulary.O -> KeyboardKey.KEY_O
        KeyVocabulary.P -> KeyboardKey.KEY_P
        KeyVocabulary.Q -> KeyboardKey.KEY_Q
        KeyVocabulary.R -> KeyboardKey.KEY_R
        KeyVocabulary.S -> KeyboardKey.KEY_S
        KeyVocabulary.T -> KeyboardKey.KEY_T
        KeyVocabulary.U -> KeyboardKey.KEY_U
        KeyVocabulary.V -> KeyboardKey.KEY_V
        KeyVocabulary.W -> KeyboardKey.KEY_W
        KeyVocabulary.X -> KeyboardKey.KEY_X
        KeyVocabulary.Y -> KeyboardKey.KEY_Y
        KeyVocabulary.Z -> KeyboardKey.KEY_Z
        KeyVocabulary.K0 -> KeyboardKey.KEY_0
        KeyVocabulary.K1 -> KeyboardKey.KEY_1
        KeyVocabulary.K2 -> KeyboardKey.KEY_2
        KeyVocabulary.K3 -> KeyboardKey.KEY_3
        KeyVocabulary.K4 -> KeyboardKey.KEY_4
        KeyVocabulary.K5 -> KeyboardKey.KEY_5
        KeyVocabulary.K6 -> KeyboardKey.KEY_6
        KeyVocabulary.K7 -> KeyboardKey.KEY_7
        KeyVocabulary.K8 -> KeyboardKey.KEY_8
        KeyVocabulary.K9 -> KeyboardKey.KEY_9
        KeyVocabulary.F1 -> KeyboardKey.KEY_F1
        KeyVocabulary.F2 -> KeyboardKey.KEY_F2
        KeyVocabulary.F3 -> KeyboardKey.KEY_F3
        KeyVocabulary.F4 -> KeyboardKey.KEY_F4
        KeyVocabulary.F5 -> KeyboardKey.KEY_F5
        KeyVocabulary.F6 -> KeyboardKey.KEY_F6
        KeyVocabulary.F7 -> KeyboardKey.KEY_F7
        KeyVocabulary.F8 -> KeyboardKey.KEY_F8
        KeyVocabulary.F9 -> KeyboardKey.KEY_F9
        KeyVocabulary.F10 -> KeyboardKey.KEY_F10
        KeyVocabulary.F11 -> KeyboardKey.KEY_F11
        KeyVocabulary.F12 -> KeyboardKey.KEY_F12
        KeyVocabulary.Up -> KeyboardKey.KEY_UP
        KeyVocabulary.Down -> KeyboardKey.KEY_DOWN
        KeyVocabulary.Left -> KeyboardKey.KEY_LEFT
        KeyVocabulary.Right -> KeyboardKey.KEY_RIGHT
        KeyVocabulary.Space -> KeyboardKey.KEY_SPACE
        KeyVocabulary.Tab -> KeyboardKey.KEY_TAB
        KeyVocabulary.Shift -> KeyboardKey.KEY_LEFT_SHIFT
        KeyVocabulary.Ctrl -> KeyboardKey.KEY_LEFT_CONTROL
        KeyVocabulary.Insert -> KeyboardKey.KEY_INSERT
        KeyVocabulary.Delete -> KeyboardKey.KEY_DELETE
        KeyVocabulary.Home -> KeyboardKey.KEY_HOME
        KeyVocabulary.End -> KeyboardKey.KEY_END
        KeyVocabulary.PageUp -> KeyboardKey.KEY_PAGE_UP
        KeyVocabulary.PageDown -> KeyboardKey.KEY_PAGE_DOWN
        KeyVocabulary.Backspace -> KeyboardKey.KEY_BACKSPACE
        KeyVocabulary.Escape -> KeyboardKey.KEY_ESCAPE
        KeyVocabulary.Enter -> KeyboardKey.KEY_ENTER
        KeyVocabulary.Pause -> KeyboardKey.KEY_PAUSE
        KeyVocabulary.ScrollLock -> KeyboardKey.KEY_SCROLL_LOCK
        KeyVocabulary.Numpad0 -> KeyboardKey.KEY_KP_0
        KeyVocabulary.Numpad1 -> KeyboardKey.KEY_KP_1
        KeyVocabulary.Numpad2 -> KeyboardKey.KEY_KP_2
        KeyVocabulary.Numpad3 -> KeyboardKey.KEY_KP_3
        KeyVocabulary.Numpad4 -> KeyboardKey.KEY_KP_4
        KeyVocabulary.Numpad5 -> KeyboardKey.KEY_KP_5
        KeyVocabulary.Numpad6 -> KeyboardKey.KEY_KP_6
        KeyVocabulary.Numpad7 -> KeyboardKey.KEY_KP_7
        KeyVocabulary.Numpad8 -> KeyboardKey.KEY_KP_8
        KeyVocabulary.Numpad9 -> KeyboardKey.KEY_KP_9
        KeyVocabulary.NumpadMultiply -> KeyboardKey.KEY_KP_MULTIPLY
        KeyVocabulary.NumpadDivide -> KeyboardKey.KEY_KP_DIVIDE
        KeyVocabulary.NumpadAdd -> KeyboardKey.KEY_KP_ADD
        KeyVocabulary.NumpadSubtract -> KeyboardKey.KEY_KP_SUBTRACT
        KeyVocabulary.NumpadDecimal -> KeyboardKey.KEY_KP_DECIMAL
        KeyVocabulary.Period -> KeyboardKey.KEY_PERIOD
        KeyVocabulary.Equals -> KeyboardKey.KEY_EQUAL
        KeyVocabulary.Comma -> KeyboardKey.KEY_COMMA
        KeyVocabulary.Minus -> KeyboardKey.KEY_MINUS
        KeyVocabulary.Oem1 -> KeyboardKey.KEY_SEMICOLON
        KeyVocabulary.Oem2 -> KeyboardKey.KEY_SLASH
        KeyVocabulary.Oem3 -> KeyboardKey.KEY_GRAVE_ACCENT
        KeyVocabulary.Oem4 -> KeyboardKey.KEY_LEFT_BRACKET
        KeyVocabulary.Oem5 -> KeyboardKey.KEY_BACKSLASH
        KeyVocabulary.Oem6 -> KeyboardKey.KEY_RIGHT_BRACKET
        KeyVocabulary.Oem7 -> KeyboardKey.KEY_APOSTROPHE
        KeyVocabulary.CapsLock -> KeyboardKey.KEY_CAPS_LOCK
    }
