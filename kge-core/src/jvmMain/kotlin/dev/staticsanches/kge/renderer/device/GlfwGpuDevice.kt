package dev.staticsanches.kge.renderer.device

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL

/**
 * JVM device over a GLFW [window] the owner created; the device owns no window.
 * [makeCurrent] makes its context current and loads the LWJGL GL capabilities
 * for the thread, and [present] swaps its buffers.
 */
internal class GlfwGpuDevice(
    private val window: Long,
) : GpuDevice {
    override fun makeCurrent() {
        GLFW.glfwMakeContextCurrent(window)
        GL.createCapabilities()
    }

    override fun present() = GLFW.glfwSwapBuffers(window)
}
