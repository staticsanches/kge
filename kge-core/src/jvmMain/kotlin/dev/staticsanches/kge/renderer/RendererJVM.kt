package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL33

actual val originalRendererImplementation: Renderer
    get() = DefaultRenderer

actual val originalDefaultClearBufferColor: Pixel
    get() = Colors.BLACK

private data object DefaultRenderer : BaseRenderer() {
    private val logger = KotlinLogging.logger {}

    private var glfwHandle: Long = -1

    override val glslVersion: String
        get() = "330 core"

    override fun beforeWindowCreation() {
        logger.debug { "Requesting OpenGL 3.3" }
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
    }

    override fun afterWindowCreation(window: Window) {
        super.afterWindowCreation(window)

        GL33.glHint(GL33.GL_PERSPECTIVE_CORRECTION_HINT, GL33.GL_NICEST)
        glfwHandle = window.mainResource
    }

    override fun displayFrame() = GLFW.glfwSwapBuffers(glfwHandle)
}
