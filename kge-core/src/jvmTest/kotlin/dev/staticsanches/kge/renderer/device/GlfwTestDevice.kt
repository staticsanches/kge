package dev.staticsanches.kge.renderer.device

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL as LwjglGL

/**
 * The JVM real-GL test device: a hidden GLFW window with an OpenGL 3.3 core
 * context, obtained on Linux and Windows CI through Mesa's software driver
 * (llvmpipe) — Xvfb on Linux, `setup-mesa-dist-win` on Windows. [detect] returns
 * null when the platform cannot provide a context (the hosted macOS runner), so
 * the real-GL probes skip.
 *
 * It owns the window and releases it on [close]: destroying the window,
 * terminating GLFW and clearing the thread-local capabilities. The device
 * behavior is the shared [GlfwGpuDevice].
 */
internal class GlfwTestDevice private constructor(
    private val window: Long,
) : GpuDevice by GlfwGpuDevice(window),
    AutoCloseable {
    val backend: String = "GLFW hidden window"

    override fun close() {
        LwjglGL.setCapabilities(null)
        GLFW.glfwDestroyWindow(window)
        GLFW.glfwTerminate()
    }

    companion object {
        fun detect(): GlfwTestDevice? = detectGlfwHiddenWindow()

        private fun detectGlfwHiddenWindow(): GlfwTestDevice? {
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
            return GlfwTestDevice(window)
        }
    }
}
