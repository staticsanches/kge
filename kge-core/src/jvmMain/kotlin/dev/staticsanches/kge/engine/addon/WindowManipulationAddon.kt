package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.GlfwWindow
import dev.staticsanches.kge.engine.HasDriver

/**
 * The JVM window operations, forwarded to the engine's production GLFW driver.
 * A custom [HasDriver.driver] without that capability fails fast.
 */
@OptIn(KGESensitiveAPI::class)
interface WindowManipulationAddon : HasDriver {
    /** Sets the window title. */
    @KGESensitiveAPI
    fun changeWindowTitle(title: String) = glfwWindow().setTitle(title)

    /** Shows the window. */
    fun showWindow() = glfwWindow().show()

    /** Hides the window. */
    fun hideWindow() = glfwWindow().hide()

    /** Whether the platform is requested to close the window. */
    @KGESensitiveAPI
    var windowShouldClose: Boolean
        get() = glfwWindow().closeRequested
        set(value) {
            glfwWindow().closeRequested = value
        }
}

@OptIn(KGESensitiveAPI::class)
private fun WindowManipulationAddon.glfwWindow(): GlfwWindow =
    driver as? GlfwWindow
        ?: error("WindowManipulationAddon requires the engine's GLFW driver")
