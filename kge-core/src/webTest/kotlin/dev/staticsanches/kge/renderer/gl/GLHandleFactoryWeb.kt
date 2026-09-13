@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.renderer.gl

import web.dom.document
import web.gl.FRAGMENT_SHADER
import web.gl.ID
import web.gl.VERTEX_SHADER
import web.gl.WebGL2RenderingContext
import web.html.HTMLCanvasElement
import kotlin.js.ExperimentalWasmJsInterop

/**
 * A shared, headless WebGL2 context used only to fabricate real DOM handles for
 * the recording backend. On Kotlin/Wasm a DOM object cannot be forged (the cast
 * to `WebGLTexture` and friends is runtime-checked), so a context-free dummy is
 * impossible while the web handles are the DOM type aliases.
 *
 * Every kind is fabricated once and reused: the handles are transported, never
 * deleted by the recording backend, so caching bounds the real context to one
 * object per kind. The throwaway program behind [uniformLocationHandle] is
 * deleted once its location is obtained.
 */
private val handleContext: WebGL2RenderingContext by lazy {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = 1
    canvas.height = 1
    checkNotNull(canvas.getContext(WebGL2RenderingContext.ID)) { "WebGL2 is required to fabricate test handles" }
}

private val textureHandle: GLTexture by lazy { checkNotNull(handleContext.createTexture()) }

private val programHandle: GLProgram by lazy { checkNotNull(handleContext.createProgram()) }

private val shaderHandle: GLShader by lazy { checkNotNull(handleContext.createShader(VERTEX_SHADER)) }

private val bufferHandle: GLBuffer by lazy { checkNotNull(handleContext.createBuffer()) }

private val vertexArrayHandle: GLVertexArrayObject by lazy { checkNotNull(handleContext.createVertexArray()) }

private val uniformLocationHandle: GLUniformLocation by lazy {
    checkNotNull(uniformLocation(handleContext)) { "the fabricating program has no active uniform" }
}

internal actual fun recordingTextureHandle(seed: Int): GLTexture = textureHandle

internal actual fun recordingProgramHandle(seed: Int): GLProgram = programHandle

internal actual fun recordingShaderHandle(seed: Int): GLShader = shaderHandle

internal actual fun recordingBufferHandle(seed: Int): GLBuffer = bufferHandle

internal actual fun recordingVertexArrayHandle(seed: Int): GLVertexArrayObject = vertexArrayHandle

internal actual fun recordingUniformLocationHandle(seed: Int): GLUniformLocation = uniformLocationHandle

/** Links a minimal program with an active `sampler2D` uniform, returning its location. */
private fun uniformLocation(gl: WebGL2RenderingContext): GLUniformLocation? {
    val vertex = checkNotNull(gl.createShader(VERTEX_SHADER))
    val fragment = checkNotNull(gl.createShader(FRAGMENT_SHADER))
    val program = checkNotNull(gl.createProgram())
    try {
        gl.shaderSource(vertex, "attribute vec4 position; void main() { gl_Position = position; }")
        gl.compileShader(vertex)
        gl.shaderSource(
            fragment,
            "uniform sampler2D texture; void main() { gl_FragColor = texture2D(texture, vec2(0.0)); }",
        )
        gl.compileShader(fragment)
        gl.attachShader(program, vertex)
        gl.attachShader(program, fragment)
        gl.linkProgram(program)
        return gl.getUniformLocation(program, "texture")
    } finally {
        gl.deleteShader(vertex)
        gl.deleteShader(fragment)
        gl.deleteProgram(program)
    }
}
