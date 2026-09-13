package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import web.gl.WebGL2RenderingContext

/**
 * The current WebGL2 context the backend operates on, or `null` while none is
 * installed.
 *
 * The device installs it process-wide through [updateGLContext] and its owner
 * clears it when the device is retired; commands fail fast while none is
 * installed.
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
