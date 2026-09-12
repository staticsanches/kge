@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.renderer.gl.GLProgram
import dev.staticsanches.kge.renderer.gl.GLShader
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLUniformLocation
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject
import dev.staticsanches.kge.renderer.gl.GLbitfield
import dev.staticsanches.kge.renderer.gl.GLboolean
import dev.staticsanches.kge.renderer.gl.GLclampf
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLintptr
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.GLsizeiptr
import dev.staticsanches.kge.renderer.gl.GLuint
import dev.staticsanches.kge.renderer.gl.gl
import js.buffer.ArrayBufferLike
import js.buffer.ArrayBufferView
import web.gl.COLOR_ATTACHMENT0
import web.gl.COMPILE_STATUS
import web.gl.FRAMEBUFFER
import web.gl.FRAMEBUFFER_BINDING
import web.gl.FRAMEBUFFER_COMPLETE
import web.gl.LINK_STATUS
import web.gl.TEXTURE_BINDING_2D
import web.gl.WebGLFramebuffer
import web.gl.WebGLTexture
import kotlin.js.ExperimentalWasmJsInterop
import js.reflect.unsafeCast as jsUnsafeCast

/**
 * Web backend (js + wasmJs): a near 1:1 mapping over the current
 * [web.gl.WebGL2RenderingContext] installed through
 * [dev.staticsanches.kge.renderer.gl.updateGLContext] by the web device
 * ([dev.staticsanches.kge.renderer.device.WebGpuDevice.makeCurrent]), whose
 * owner clears it when the device is retired. The engine's int `GLenum`s are
 * reinterpreted as the binding's opaque `GLenum` type; compile and link
 * failures are checked here and thrown with the driver's info log.
 */
internal object WebGLService : GLService {
    // Texture

    override fun createTexture(): GLTexture = checkNotNull(gl.createTexture()) { "Unable to create a GL texture" }

    override fun deleteTexture(texture: GLTexture) = gl.deleteTexture(texture)

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) = gl.bindTexture(target.asGLenum(), texture)

    override fun texParameteri(
        target: GLenum,
        pname: GLenum,
        param: GLenum,
    ) = gl.texParameteri(target.asGLenum(), pname.asGLenum(), param.asGLenum())

    override fun texImage2D(
        target: GLenum,
        level: GLint,
        internalFormat: GLenum,
        width: GLsizei,
        height: GLsizei,
        border: GLint,
        format: GLenum,
        type: GLenum,
        srcData: ByteBuffer?,
    ) = gl.texImage2D(
        target.asGLenum(),
        level,
        internalFormat.asGLenum(),
        width,
        height,
        border,
        format.asGLenum(),
        type.asGLenum(),
        srcData?.asArrayBufferView(),
    )

    override fun texSubImage2D(
        target: GLenum,
        level: GLint,
        xOffset: GLint,
        yOffset: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        srcData: ByteBuffer,
    ) = gl.texSubImage2D(
        target.asGLenum(),
        level,
        xOffset,
        yOffset,
        width,
        height,
        format.asGLenum(),
        type.asGLenum(),
        srcData.asArrayBufferView(),
    )

    override fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) = gl.readPixels(x, y, width, height, format.asGLenum(), type.asGLenum(), dstData.asArrayBufferView())

    override fun getTexImage(
        target: GLenum,
        level: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) {
        // WebGL2 has no getTexImage: attach the currently bound texture to an
        // internal framebuffer and read it back, restoring the previous
        // framebuffer binding afterwards.
        val texture = jsUnsafeCast<WebGLTexture>(gl.getParameter(TEXTURE_BINDING_2D))
        val previous = jsUnsafeCast<WebGLFramebuffer>(gl.getParameter(FRAMEBUFFER_BINDING))
        val framebuffer = checkNotNull(gl.createFramebuffer()) { "Unable to create a GL framebuffer" }
        try {
            gl.bindFramebuffer(FRAMEBUFFER, framebuffer)
            gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, target.asGLenum(), texture, level)
            check(gl.checkFramebufferStatus(FRAMEBUFFER).toString() == FRAMEBUFFER_COMPLETE.toString()) {
                "Unable to read the texture back: incomplete framebuffer"
            }
            gl.readPixels(0, 0, width, height, format.asGLenum(), type.asGLenum(), dstData.asArrayBufferView())
        } finally {
            gl.bindFramebuffer(FRAMEBUFFER, previous)
            gl.deleteFramebuffer(framebuffer)
        }
    }

    // Shader

    override fun createShader(type: GLenum): GLShader =
        checkNotNull(gl.createShader(type.asGLenum())) { "Unable to create a GL shader" }

    override fun deleteShader(shader: GLShader) = gl.deleteShader(shader)

    override fun shaderSource(
        shader: GLShader,
        source: String,
    ) = gl.shaderSource(shader, source)

    override fun compileShader(shader: GLShader) {
        gl.compileShader(shader)
        check(glStatusTrue(gl.getShaderParameter(shader, COMPILE_STATUS))) {
            "Unable to compile GL shader: ${gl.getShaderInfoLog(shader)}"
        }
    }

    // Program

    override fun createProgram(): GLProgram = gl.createProgram()

    override fun deleteProgram(program: GLProgram) = gl.deleteProgram(program)

    override fun attachShader(
        program: GLProgram,
        shader: GLShader,
    ) = gl.attachShader(program, shader)

    override fun linkProgram(program: GLProgram) {
        gl.linkProgram(program)
        check(glStatusTrue(gl.getProgramParameter(program, LINK_STATUS))) {
            "Unable to link GL program: ${gl.getProgramInfoLog(program)}"
        }
    }

    override fun useProgram(program: GLProgram?) = gl.useProgram(program)

    // Uniform

    override fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation? = gl.getUniformLocation(program, name)

    override fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    ) = gl.uniform1i(location, x)

    // Buffer

    override fun createBuffer(): GLBuffer = checkNotNull(gl.createBuffer()) { "Unable to create a GL buffer" }

    override fun deleteBuffer(buffer: GLBuffer) = gl.deleteBuffer(buffer)

    override fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    ) = gl.bindBuffer(target.asGLenum(), buffer)

    override fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        usage: GLenum,
    ) = gl.bufferData(target.asGLenum(), srcData.asArrayBufferView(), usage.asGLenum())

    override fun bufferData(
        target: GLenum,
        size: GLsizeiptr,
        usage: GLenum,
    ) = gl.bufferData(target.asGLenum(), size, usage.asGLenum())

    override fun bufferSubData(
        target: GLenum,
        dstByteOffset: GLintptr,
        srcData: ByteBuffer,
    ) = gl.bufferSubData(target.asGLenum(), dstByteOffset, srcData.asArrayBufferView())

    // Vertex array object

    override fun createVertexArray(): GLVertexArrayObject =
        checkNotNull(gl.createVertexArray()) { "Unable to create a GL vertex array" }

    override fun deleteVertexArray(vao: GLVertexArrayObject) = gl.deleteVertexArray(vao)

    override fun bindVertexArray(vao: GLVertexArrayObject?) = gl.bindVertexArray(vao)

    // Attribute

    override fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    ) = gl.vertexAttribPointer(index.asGLuint(), size, type.asGLenum(), normalized, stride, offset)

    override fun enableVertexAttribArray(index: GLuint) = gl.enableVertexAttribArray(index.asGLuint())

    // Draw

    override fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    ) = gl.drawArrays(mode.asGLenum(), first, count)

    // State

    override fun enable(cap: GLenum) = gl.enable(cap.asGLenum())

    override fun disable(cap: GLenum) = gl.disable(cap.asGLenum())

    override fun blendFunc(
        src: GLenum,
        dst: GLenum,
    ) = gl.blendFunc(src.asGLenum(), dst.asGLenum())

    override fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    ) = gl.clearColor(red, green, blue, alpha)

    override fun clear(mask: GLbitfield) = gl.clear(mask.asGLbitfield())

    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) = gl.viewport(x, y, width, height)
}

private fun Int.asGLenum(): web.gl.GLenum = jsUnsafeCast(this)

private fun Int.asGLuint(): web.gl.GLuint = jsUnsafeCast(this)

private fun Int.asGLbitfield(): web.gl.GLbitfield = jsUnsafeCast(this)

/**
 * Reads a GL status query as a Kotlin boolean. The binding returns a JS
 * boolean, which compares unequal to Kotlin `true` on Kotlin/Wasm (its
 * `toString()` is still `"true"`), so the equality check is not portable.
 */
private fun glStatusTrue(value: Any?): Boolean = value == true || value?.toString() == "true"

/** Reinterprets the engine buffer's backing `Uint8Array` as the binding's view type. */
private fun ByteBuffer.asArrayBufferView(): ArrayBufferView<ArrayBufferLike> = jsUnsafeCast(nativeBytes)
