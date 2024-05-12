package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.input.KeyboardKey
import dev.staticsanches.kge.input.KeyboardKeyAction
import dev.staticsanches.kge.input.KeyboardModifiers
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.APIUtil
import java.lang.reflect.Field

interface CallbacksAddon {
    /**
     * Called once on application startup, use to load your resources.
     */
    context(Window)
    fun onUserCreate() = Unit

    /**
     * Called every frame.
     *
     * @return if the game should continue running.
     */
    context(Window)
    fun onUserUpdate(): Boolean = true

    /**
     * Called once on application termination, so you can clean the loaded resources.
     *
     * @return if the shutdown process should continue.
     */
    context(Window)
    fun onUserDestroy(): Boolean = true

    /**
     * Handle [GLFW] errors.
     */
    fun onGLFWError(
        error: Int,
        message: Long,
    ): Unit = throw IllegalStateException("[GLFW] Error ${error.glfwErrorCode}: ${message.glfwErrorDescription}")

    /**
     * Handle window resize.
     */
    context(Window)
    fun onFrameBufferResize(
        width: Int,
        height: Int,
    ) = Unit

    /**
     * Handle keyboard events.
     */
    context(Window)
    fun onKeyEvent(
        key: KeyboardKey,
        newAction: KeyboardKeyAction,
        scancode: Int,
        newModifiers: KeyboardModifiers,
    ) = Unit

    /**
     * The associated [GLFW] error code representation.
     */
    val Int.glfwErrorCode: String
        get() = errorCodes[this] ?: this.toString()

    val Long.glfwErrorDescription: String
        get() = GLFWErrorCallback.getDescription(this)

    companion object {
        private val errorCodes =
            APIUtil.apiClassTokens(
                { _: Field?, value: Int -> value in 0x10001..0x1ffff },
                null,
                GLFW::class.java,
            ).toMap()
    }
}
