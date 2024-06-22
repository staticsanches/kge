@file:Suppress("unused")

package dev.staticsanches.kge.input

import org.lwjgl.glfw.GLFW

enum class MouseButton {
    BUTTON_LEFT,
    BUTTON_RIGHT,
    BUTTON_MIDDLE,
    BUTTON_4,
    BUTTON_5,
    BUTTON_6,
    BUTTON_7,
    BUTTON_8,
    ;

    companion object {
        operator fun get(glfwCode: Int): MouseButton? =
            when (glfwCode) {
                GLFW.GLFW_MOUSE_BUTTON_LEFT -> BUTTON_LEFT
                GLFW.GLFW_MOUSE_BUTTON_RIGHT -> BUTTON_RIGHT
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> BUTTON_MIDDLE
                GLFW.GLFW_MOUSE_BUTTON_4 -> BUTTON_4
                GLFW.GLFW_MOUSE_BUTTON_5 -> BUTTON_5
                GLFW.GLFW_MOUSE_BUTTON_6 -> BUTTON_6
                GLFW.GLFW_MOUSE_BUTTON_7 -> BUTTON_7
                GLFW.GLFW_MOUSE_BUTTON_8 -> BUTTON_8
                else -> null
            }
    }
}
