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
import org.lwjgl.opengl.GL33

/**
 * JVM backend: a near 1:1 mapping over LWJGL's `GL33` (OpenGL 3.3 core).
 * Compile and link failures are thrown with the driver's info log, and
 * `getUniformLocation` normalizes `-1` to `null`.
 */
internal object LwjglGLService : GLService {
    // Texture

    override fun createTexture(): GLTexture = GLTexture(GL33.glGenTextures())

    override fun deleteTexture(texture: GLTexture) = GL33.glDeleteTextures(texture.id)

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) = GL33.glBindTexture(target, texture?.id ?: 0)

    override fun texParameteri(
        target: GLenum,
        pname: GLenum,
        param: GLenum,
    ) = GL33.glTexParameteri(target, pname, param)

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
    ) = GL33.glTexImage2D(target, level, internalFormat, width, height, border, format, type, srcData)

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
    ) = GL33.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, srcData)

    override fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) = GL33.glReadPixels(x, y, width, height, format, type, dstData)

    override fun getTexImage(
        target: GLenum,
        level: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) = GL33.glGetTexImage(target, level, format, type, dstData)

    // Shader

    override fun createShader(type: GLenum): GLShader = GLShader(GL33.glCreateShader(type))

    override fun deleteShader(shader: GLShader) = GL33.glDeleteShader(shader.id)

    override fun shaderSource(
        shader: GLShader,
        source: String,
    ) = GL33.glShaderSource(shader.id, source)

    override fun compileShader(shader: GLShader) {
        GL33.glCompileShader(shader.id)
        check(GL33.glGetShaderi(shader.id, GL33.GL_COMPILE_STATUS) == GL33.GL_TRUE) {
            "Unable to compile GL shader ${shader.id}: ${GL33.glGetShaderInfoLog(shader.id)}"
        }
    }

    // Program

    override fun createProgram(): GLProgram = GLProgram(GL33.glCreateProgram())

    override fun deleteProgram(program: GLProgram) = GL33.glDeleteProgram(program.id)

    override fun attachShader(
        program: GLProgram,
        shader: GLShader,
    ) = GL33.glAttachShader(program.id, shader.id)

    override fun linkProgram(program: GLProgram) {
        GL33.glLinkProgram(program.id)
        check(GL33.glGetProgrami(program.id, GL33.GL_LINK_STATUS) == GL33.GL_TRUE) {
            "Unable to link GL program ${program.id}: ${GL33.glGetProgramInfoLog(program.id)}"
        }
    }

    override fun useProgram(program: GLProgram?) = GL33.glUseProgram(program?.id ?: 0)

    // Uniform

    override fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation? {
        val location = GL33.glGetUniformLocation(program.id, name)
        return if (location == -1) null else GLUniformLocation(location)
    }

    override fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    ) = GL33.glUniform1i(location.id, x)

    // Buffer

    override fun createBuffer(): GLBuffer = GLBuffer(GL33.glGenBuffers())

    override fun deleteBuffer(buffer: GLBuffer) = GL33.glDeleteBuffers(buffer.id)

    override fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    ) = GL33.glBindBuffer(target, buffer?.id ?: 0)

    override fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        byteCount: GLsizeiptr,
        usage: GLenum,
    ) {
        val view = srcData.duplicate()
        view.position(0)
        view.limit(byteCount)
        GL33.glBufferData(target, view, usage)
    }

    override fun bufferData(
        target: GLenum,
        size: GLsizeiptr,
        usage: GLenum,
    ) = GL33.glBufferData(target, size.toLong(), usage)

    override fun bufferSubData(
        target: GLenum,
        dstByteOffset: GLintptr,
        srcData: ByteBuffer,
    ) = GL33.glBufferSubData(target, dstByteOffset.toLong(), srcData)

    // Vertex array object

    override fun createVertexArray(): GLVertexArrayObject = GLVertexArrayObject(GL33.glGenVertexArrays())

    override fun deleteVertexArray(vao: GLVertexArrayObject) = GL33.glDeleteVertexArrays(vao.id)

    override fun bindVertexArray(vao: GLVertexArrayObject?) = GL33.glBindVertexArray(vao?.id ?: 0)

    // Attribute

    override fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    ) = GL33.glVertexAttribPointer(index, size, type, normalized, stride, offset.toLong())

    override fun enableVertexAttribArray(index: GLuint) = GL33.glEnableVertexAttribArray(index)

    // Draw

    override fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    ) = GL33.glDrawArrays(mode, first, count)

    // State

    override fun enable(cap: GLenum) = GL33.glEnable(cap)

    override fun disable(cap: GLenum) = GL33.glDisable(cap)

    override fun blendFunc(
        src: GLenum,
        dst: GLenum,
    ) = GL33.glBlendFunc(src, dst)

    override fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    ) = GL33.glClearColor(red, green, blue, alpha)

    override fun clear(mask: GLbitfield) = GL33.glClear(mask)

    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) = GL33.glViewport(x, y, width, height)
}
