package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import org.lwjgl.glfw.GLFW

interface WindowManipulationAddon : WindowDependentAddon {
    @KGESensitiveAPI
    fun changeWindowTitle(title: String) = GLFW.glfwSetWindowTitle(glfwHandle, title)

    fun showWindow() = GLFW.glfwShowWindow(glfwHandle)

    fun hideWindow() = GLFW.glfwHideWindow(glfwHandle)

    @KGESensitiveAPI
    var windowShouldClose: Boolean
        get() = GLFW.glfwWindowShouldClose(glfwHandle)
        set(value) = GLFW.glfwSetWindowShouldClose(glfwHandle, value)
}
