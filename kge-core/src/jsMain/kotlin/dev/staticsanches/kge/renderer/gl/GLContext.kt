package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import web.gl.WEBGL_multi_draw
import web.gl.WebGL2RenderingContext
import web.gl.WebGLExtension

/**
 * Global access to the [WebGL2RenderingContext] available for the engine.
 */
@KGESensitiveAPI
val gl: WebGL2RenderingContext
    get() = internalGL ?: throw IllegalStateException("GL context is not initialized")

@KGESensitiveAPI
val glMultiDraw: WEBGL_multi_draw
    get() = internalGLMultiDraw ?: throw IllegalStateException("GL context is not initialized")

@KGESensitiveAPI
fun updateGLContext(gl: WebGL2RenderingContext?) {
    if (gl == null) {
        internalGLMultiDraw = null
        internalGL = null
    } else {
        internalGLMultiDraw = gl.getExtension(WebGLExtension.WEBGL_multi_draw)
            ?: throw IllegalStateException("WEBGL_multi_draw extension is not available")
        internalGL = gl
    }
}

private var internalGL: WebGL2RenderingContext? = null
private var internalGLMultiDraw: WEBGL_multi_draw? = null
