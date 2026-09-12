package dev.staticsanches.kge.renderer.device

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL

/**
 * JVM device over a GLFW window the owner created (the engine's window concept
 * and the hidden-window test harness). The device owns no window: it only makes
 * the existing [window]'s context current — loading the LWJGL GL capabilities
 * for the thread — and swaps its buffers.
 *
 * @param window the GLFW window handle whose context this device drives.
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
