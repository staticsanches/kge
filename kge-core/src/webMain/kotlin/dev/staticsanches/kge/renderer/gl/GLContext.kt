package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import web.gl.WebGL2RenderingContext

/**
 * The current WebGL2 context the [dev.staticsanches.kge.renderer.gl.service.WebGLService]
 * backend operates on, or `null` while none is installed.
 *
 * The web device installs this process-wide through [updateGLContext]; the
 * device's owner clears it when the device is retired. The test harness does
 * both; an external web device/backend follows the same two calls. Every
 * command fails fast while no context is installed.
 */
@KGESensitiveAPI
var glContext: WebGL2RenderingContext? = null
    private set

/** Installs [context] as the process-wide WebGL2 context, or clears it when `null`. */
@KGESensitiveAPI
fun updateGLContext(context: WebGL2RenderingContext?) {
    glContext = context
}

/** The backend convenience over [glContext]: fail fast while no context is installed. */
internal val gl: WebGL2RenderingContext
    get() = glContext ?: throw IllegalStateException("GL context is not initialized")
