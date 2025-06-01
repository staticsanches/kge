@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import org.lwjgl.glfw.GLFW

interface WindowManipulationAddon : WindowDependentAddon {
    @KGESensitiveAPI
    fun changeWindowTitle(title: String) = GLFW.glfwSetWindowTitle(mainResource, title)

    fun showWindow() = GLFW.glfwShowWindow(mainResource)

    fun hideWindow() = GLFW.glfwHideWindow(mainResource)

    @KGESensitiveAPI
    var windowShouldClose: Boolean
        get() = GLFW.glfwWindowShouldClose(mainResource)
        set(value) = GLFW.glfwSetWindowShouldClose(mainResource, value)
}
