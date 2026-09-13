package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.device.GlfwGpuDevice
import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.resource.letClosingIfFailed
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Platform

internal actual val driverServiceDefault: DriverService = GlfwDriverService

internal object GlfwDriverService : DriverService {
    override fun create(config: WindowConfig): Driver = create(config, visible = true)

    /** Opens the production driver; a hidden window serves the real-GL smoke. */
    fun create(
        config: WindowConfig,
        visible: Boolean,
    ): Driver {
        if (Platform.get() == Platform.MACOSX) Configuration.GLFW_LIBRARY_NAME.set("glfw_async")

        check(GLFW.glfwInit()) { "Unable to initialize GLFW" }

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, if (visible) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, if (config.resizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)

        val window =
            GLFW.glfwCreateWindow(
                config.screenWidth * config.pixelWidth,
                config.screenHeight * config.pixelHeight,
                config.title,
                if (config.fullScreen) GLFW.glfwGetPrimaryMonitor() else MemoryUtil.NULL,
                MemoryUtil.NULL,
            )
        if (window == MemoryUtil.NULL) {
            GLFW.glfwTerminate()
            error("Failed to create the GLFW window")
        }

        return GlfwDriver(window).letClosingIfFailed { driver ->
            driver.makeCurrent()
            GLFW.glfwSwapInterval(if (config.vsync) 1 else 0)
            driver
        }
    }
}

private class GlfwDriver(
    private val window: Long,
) : Driver,
    GpuDevice by GlfwGpuDevice(window) {
    private var closed = false

    // GLFW writes into caller-owned arrays; reuse them instead of allocating
    // two per size query on the render hot path.
    private val width = IntArray(1)
    private val height = IntArray(1)

    override fun pollEvents() = GLFW.glfwPollEvents()

    override suspend fun awaitNextFrame() = Unit

    override fun windowSize(): Int2D {
        GLFW.glfwGetWindowSize(window, width, height)
        return Int2D(width[0], height[0])
    }

    override fun framebufferSize(): Int2D {
        GLFW.glfwGetFramebufferSize(window, width, height)
        return Int2D(width[0], height[0])
    }

    override fun isClosing(): Boolean = closed || GLFW.glfwWindowShouldClose(window)

    override fun cancelClose() = GLFW.glfwSetWindowShouldClose(window, false)

    override fun close() {
        if (closed) return
        closed = true
        GL.setCapabilities(null)
        GLFW.glfwDestroyWindow(window)
        GLFW.glfwTerminate()
    }
}
