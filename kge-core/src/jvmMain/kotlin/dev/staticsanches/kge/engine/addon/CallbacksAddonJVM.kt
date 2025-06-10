@file:Suppress("ktlint:standard:filename", "unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.buffer.wrapper.ByteBufferWrapper
import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardKeyAction
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.APIUtil
import java.lang.reflect.Field

actual interface CallbacksAddon {
    /**
     * Called once on application startup, use to load your resources.
     */
    fun onUserCreate() = Unit

    /**
     * Called every frame.
     *
     * @return if the game should continue running.
     */
    fun onUserUpdate(): Boolean = true

    /**
     * Called once on application termination, so you can clean the loaded resources.
     *
     * @return if the shutdown process should continue.
     */
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
    fun onFrameBufferResize(
        width: Int,
        height: Int,
    ) = Unit

    /**
     * Handle keyboard events.
     */
    fun onKeyEvent(
        key: KeyboardKey,
        newAction: KeyboardKeyAction,
        scancode: Int,
        newModifiers: KeyboardModifiers,
    ) = Unit

    /**
     * Handle file drop events. The return is used to read file contents and call [onFileOpenEvent].
     */
    fun onFileDropEvent(fileNames: List<String>): List<String>? = null

    /**
     * Handle file open events. Works together with [onFileDropEvent]. It MUST close the resources.
     */
    fun onFileOpenEvent(files: Map<String, ByteBufferWrapper>) = Unit

    /**
     * The associated [GLFW] error code representation.
     */
    val Int.glfwErrorCode: String
        get() = errorCodes[this] ?: this.toString()

    val Long.glfwErrorDescription: String
        get() = GLFWErrorCallback.getDescription(this)

    companion object {
        private val errorCodes =
            APIUtil
                .apiClassTokens(
                    { _: Field?, value: Int -> value in 0x10001..0x1ffff },
                    null,
                    GLFW::class.java,
                ).toMap()
    }
}
