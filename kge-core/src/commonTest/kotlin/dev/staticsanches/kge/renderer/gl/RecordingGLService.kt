package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.service.GLService

/** One command observed by [RecordingGLService]: the method name and its raw arguments. */
internal data class RecordedGLCall(
    val name: String,
    val arguments: List<Any?>,
)

/**
 * The recording [GLService] test backend: it captures every command in call
 * order instead of touching a GL context, and fabricates the handles the
 * `create*` commands must return through the platform test factories. Because
 * it is a plain service override, the common renderer can be driven through it
 * on every target with no GPU.
 */
internal class RecordingGLService : GLService {
    private val mutableCalls = mutableListOf<RecordedGLCall>()
    val calls: List<RecordedGLCall> get() = mutableCalls

    /** The handle returned by the most recent [createTexture], for call-sequence assertions. */
    var lastCreatedTexture: GLTexture? = null
        private set

    /** The handle returned by the most recent [createShader], for call-sequence assertions. */
    var lastCreatedShader: GLShader? = null
        private set

    /** The handle returned by the most recent [createProgram], for call-sequence assertions. */
    var lastCreatedProgram: GLProgram? = null
        private set

    /** The handle returned by the most recent [createBuffer], for call-sequence assertions. */
    var lastCreatedBuffer: GLBuffer? = null
        private set

    /** The handle returned by the most recent [createVertexArray], for call-sequence assertions. */
    var lastCreatedVertexArray: GLVertexArrayObject? = null
        private set

    private var nextSeed = 0

    /** Forgets every recorded call, keeping the last-created handles. */
    fun clear() {
        mutableCalls.clear()
    }

    private fun record(
        name: String,
        vararg arguments: Any?,
    ) {
        mutableCalls += RecordedGLCall(name, arguments.toList())
    }

    // Texture

    override fun createTexture(): GLTexture {
        val handle = recordingTextureHandle(nextSeed++)
        lastCreatedTexture = handle
        record("createTexture")
        return handle
    }

    override fun deleteTexture(texture: GLTexture) = record("deleteTexture", texture)

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) = record("bindTexture", target, texture)

    override fun texParameteri(
        target: GLenum,
        pname: GLenum,
        param: GLenum,
    ) = record("texParameteri", target, pname, param)

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
    ) = record("texImage2D", target, level, internalFormat, width, height, border, format, type, srcData)

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
    ) = record("texSubImage2D", target, level, xOffset, yOffset, width, height, format, type, srcData)

    override fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) = record("readPixels", x, y, width, height, format, type, dstData)

    override fun getTexImage(
        target: GLenum,
        level: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) = record("getTexImage", target, level, width, height, format, type, dstData)

    // Shader

    override fun createShader(type: GLenum): GLShader {
        val handle = recordingShaderHandle(nextSeed++)
        lastCreatedShader = handle
        record("createShader", type)
        return handle
    }

    override fun deleteShader(shader: GLShader) = record("deleteShader", shader)

    override fun shaderSource(
        shader: GLShader,
        source: String,
    ) = record("shaderSource", shader, source)

    override fun compileShader(shader: GLShader) = record("compileShader", shader)

    // Program

    override fun createProgram(): GLProgram {
        val handle = recordingProgramHandle(nextSeed++)
        lastCreatedProgram = handle
        record("createProgram")
        return handle
    }

    override fun deleteProgram(program: GLProgram) = record("deleteProgram", program)

    override fun attachShader(
        program: GLProgram,
        shader: GLShader,
    ) = record("attachShader", program, shader)

    override fun linkProgram(program: GLProgram) = record("linkProgram", program)

    override fun useProgram(program: GLProgram?) = record("useProgram", program)

    // Uniform

    override fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation? {
        val location = recordingUniformLocationHandle(nextSeed++)
        record("getUniformLocation", program, name, location)
        return location
    }

    override fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    ) = record("uniform1i", location, x)

    // Buffer

    override fun createBuffer(): GLBuffer {
        val handle = recordingBufferHandle(nextSeed++)
        lastCreatedBuffer = handle
        record("createBuffer")
        return handle
    }

    override fun deleteBuffer(buffer: GLBuffer) = record("deleteBuffer", buffer)

    override fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    ) = record("bindBuffer", target, buffer)

    override fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        usage: GLenum,
    ) = record("bufferData", target, srcData, usage)

    override fun bufferData(
        target: GLenum,
        size: GLsizeiptr,
        usage: GLenum,
    ) = record("bufferData", target, size, usage)

    override fun bufferSubData(
        target: GLenum,
        dstByteOffset: GLintptr,
        srcData: ByteBuffer,
    ) = record("bufferSubData", target, dstByteOffset, srcData)

    // Vertex array object

    override fun createVertexArray(): GLVertexArrayObject {
        val handle = recordingVertexArrayHandle(nextSeed++)
        lastCreatedVertexArray = handle
        record("createVertexArray")
        return handle
    }

    override fun deleteVertexArray(vao: GLVertexArrayObject) = record("deleteVertexArray", vao)

    override fun bindVertexArray(vao: GLVertexArrayObject?) = record("bindVertexArray", vao)

    // Attribute

    override fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    ) = record("vertexAttribPointer", index, size, type, normalized, stride, offset)

    override fun enableVertexAttribArray(index: GLuint) = record("enableVertexAttribArray", index)

    // Draw

    override fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    ) = record("drawArrays", mode, first, count)

    // State

    override fun enable(cap: GLenum) = record("enable", cap)

    override fun disable(cap: GLenum) = record("disable", cap)

    override fun blendFunc(
        src: GLenum,
        dst: GLenum,
    ) = record("blendFunc", src, dst)

    override fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    ) = record("clearColor", red, green, blue, alpha)

    override fun clear(mask: GLbitfield) = record("clear", mask)

    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) = record("viewport", x, y, width, height)
}
