package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.Modifiers
import dev.staticsanches.kge.engine.input.MouseButton
import dev.staticsanches.kge.engine.input.RawInput
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.device.GlfwGpuDevice
import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.resource.letClosingIfFailed
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWCursorPosCallback
import org.lwjgl.glfw.GLFWKeyCallback
import org.lwjgl.glfw.GLFWMouseButtonCallback
import org.lwjgl.glfw.GLFWScrollCallback
import org.lwjgl.glfw.GLFWWindowFocusCallback
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
            driver.installCallbacks()
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
    override val input = RawInput()

    private var closed = false

    // GLFW writes into caller-owned arrays; reuse them instead of allocating
    // two per size query on the render hot path.
    private val width = IntArray(1)
    private val height = IntArray(1)

    private var keyCallback: GLFWKeyCallback? = null
    private var mouseButtonCallback: GLFWMouseButtonCallback? = null
    private var cursorPosCallback: GLFWCursorPosCallback? = null
    private var scrollCallback: GLFWScrollCallback? = null
    private var focusCallback: GLFWWindowFocusCallback? = null

    /**
     * Installs the GLFW callbacks and retains them: `glfwSet*Callback` returns
     * the *previous* callback, so the created one must be held to be freed on
     * close. Called inside the creation guard, so a failure tears the window
     * down with the callbacks already installed.
     */
    fun installCallbacks() {
        keyCallback =
            GLFWKeyCallback.create { _, key, _, action, mods -> input.applyKey(key, action, mods) }.set(window)
        mouseButtonCallback =
            GLFWMouseButtonCallback
                .create {
                    _,
                    button,
                    action,
                    _,
                    ->
                    input.applyMouseButton(button, action)
                }.set(window)
        cursorPosCallback = GLFWCursorPosCallback.create { _, x, y -> input.applyMouseMove(x, y) }.set(window)
        scrollCallback = GLFWScrollCallback.create { _, _, y -> input.applyScroll(y) }.set(window)
        focusCallback = GLFWWindowFocusCallback.create { _, focused -> input.applyFocus(focused) }.set(window)
    }

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
        keyCallback?.free()
        mouseButtonCallback?.free()
        cursorPosCallback?.free()
        scrollCallback?.free()
        focusCallback?.free()
        GLFW.glfwTerminate()
    }
}

/** Applies a GLFW key event; `GLFW_REPEAT` keeps the key down without a new edge. */
internal fun RawInput.applyKey(
    glfwKey: Int,
    action: Int,
    mods: Int,
) {
    modifiers = glfwModifiers(mods)
    when (action) {
        GLFW.GLFW_PRESS, GLFW.GLFW_REPEAT -> setKeyDown(KeyboardKey[glfwKey], true)
        GLFW.GLFW_RELEASE -> setKeyDown(KeyboardKey[glfwKey], false)
    }
}

/** Applies a GLFW mouse button event; an unknown button is ignored. */
internal fun RawInput.applyMouseButton(
    glfwButton: Int,
    action: Int,
) {
    val button =
        when (glfwButton) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButton.LEFT
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButton.RIGHT
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButton.MIDDLE
            else -> return
        }
    setMouseButtonDown(button, action == GLFW.GLFW_PRESS)
}

/** Applies a GLFW cursor position, in window points. */
internal fun RawInput.applyMouseMove(
    x: Double,
    y: Double,
) {
    mousePosition = Int2D(x.toInt(), y.toInt())
}

/** Applies a GLFW scroll offset; GLFW reports positive away from the user. */
internal fun RawInput.applyScroll(delta: Double) {
    wheelDelta += delta.toInt()
}

/** Applies a focus change; losing focus releases every held key and mouse button. */
internal fun RawInput.applyFocus(focused: Boolean) {
    this.focused = focused
    if (!focused) {
        clearKeys()
        clearMouseButtons()
    }
}

internal fun glfwModifiers(mods: Int): Modifiers =
    Modifiers.of(
        shift = mods and GLFW.GLFW_MOD_SHIFT != 0,
        ctrl = mods and GLFW.GLFW_MOD_CONTROL != 0,
        alt = mods and GLFW.GLFW_MOD_ALT != 0,
        superKey = mods and GLFW.GLFW_MOD_SUPER != 0,
        capsLock = mods and GLFW.GLFW_MOD_CAPS_LOCK != 0,
        numLock = mods and GLFW.GLFW_MOD_NUM_LOCK != 0,
    )
