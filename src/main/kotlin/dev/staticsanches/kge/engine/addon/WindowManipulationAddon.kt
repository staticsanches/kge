package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.window.Window
import org.lwjgl.glfw.GLFW

@OptIn(KGESensitiveAPI::class)
interface WindowManipulationAddon {

	context(Window)
	@KGESensitiveAPI
	fun changeWindowTitle(title: String) =
		GLFW.glfwSetWindowTitle(glfwHandle, title)

	context(Window)
	fun showWindow() =
		GLFW.glfwShowWindow(glfwHandle)

	context(Window)
	fun hideWindow() =
		GLFW.glfwHideWindow(glfwHandle)

	context(Window)
	@KGESensitiveAPI
	var windowShouldClose: Boolean
		get() = GLFW.glfwWindowShouldClose(glfwHandle)
		set(value) = GLFW.glfwSetWindowShouldClose(glfwHandle, value)

}
