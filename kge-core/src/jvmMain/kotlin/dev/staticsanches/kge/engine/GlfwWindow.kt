package dev.staticsanches.kge.engine

/**
 * The GLFW window operations a JVM [Driver] can expose. The JVM window addon
 * reaches the window through this capability, so it contains no GLFW statics.
 */
internal interface GlfwWindow {
    /** Sets the window title. */
    fun setTitle(title: String)

    /** Shows the window. */
    fun show()

    /** Hides the window. */
    fun hide()

    /** Whether the platform is requested to close the window. */
    var closeRequested: Boolean
}
