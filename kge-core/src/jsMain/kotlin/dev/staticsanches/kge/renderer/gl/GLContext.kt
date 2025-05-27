@file:Suppress("unused")

package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import web.gl.WebGL2RenderingContext

/**
 * Global access to the [WebGL2RenderingContext] available for the engine.
 */
@KGESensitiveAPI
val gl: WebGL2RenderingContext
    get() = glContext ?: throw IllegalStateException("GL context is not initialized")

@KGESensitiveAPI
fun updateGLContext(gl: WebGL2RenderingContext?) {
    glContext = gl
}

private var glContext: WebGL2RenderingContext? = null
