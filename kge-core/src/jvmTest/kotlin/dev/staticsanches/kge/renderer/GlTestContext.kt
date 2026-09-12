package dev.staticsanches.kge.renderer

import org.lwjgl.glfw.GLFW

/**
 * SPIKE helper: the production GLFW context path, headless. Creates a hidden
 * GLFW window with an OpenGL 3.3 core context, which the Linux and Windows CI
 * runners obtain through Mesa's software driver (llvmpipe) — Xvfb on Linux,
 * `setup-mesa-dist-win` on Windows. Returns null when the platform cannot give
 * a context (the hosted macOS runner), which makes the probe skip.
 *
 * Not production code — C9 will define the real context/device seam.
 */
internal interface GlTestContext : AutoCloseable {
    val backend: String

    fun makeCurrent()

    companion object {
        fun detect(): GlTestContext? = detectGlfwHiddenWindow()
    }
}

private class GlfwContext(
    private val window: Long,
) : GlTestContext {
    override val backend: String = "GLFW hidden window"

    override fun makeCurrent() = GLFW.glfwMakeContextCurrent(window)

    override fun close() {
        GLFW.glfwDestroyWindow(window)
        GLFW.glfwTerminate()
    }
}

private fun detectGlfwHiddenWindow(): GlTestContext? {
    if (!GLFW.glfwInit()) return null

    GLFW.glfwDefaultWindowHints()
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)

    val window = GLFW.glfwCreateWindow(64, 64, "kge-gl-probe", 0L, 0L)
    if (window == 0L) {
        GLFW.glfwTerminate()
        return null
    }
    return GlfwContext(window)
}
