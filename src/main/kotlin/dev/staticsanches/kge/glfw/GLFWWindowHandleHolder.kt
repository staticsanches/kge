package dev.staticsanches.kge.glfw

import dev.staticsanches.kge.glfw.GLFWWindowHandleHolder.handle
import org.lwjgl.glfw.GLFW


/**
 * Holder for the [GLFW] window [handle].
 */
object GLFWWindowHandleHolder {

	@Volatile
	private var _handle: Long? = null

	val handle: Long
		get() = _handle ?: throw RuntimeException("GLFW has not been initialized")

	internal fun initialize(handle: Long, block: () -> Unit) =
		synchronized(GLFWWindowHandleHolder) {
			check(_handle == null) { "GLFW has already been initialized" }
			try {
				_handle = handle
				block()
			} finally {
				_handle = null
			}
		}

}
