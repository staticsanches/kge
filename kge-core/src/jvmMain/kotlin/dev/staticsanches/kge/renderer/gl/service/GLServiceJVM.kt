package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
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
import dev.staticsanches.kge.renderer.gl.GLfloat
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLintptr
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.GLsizeiptr
import dev.staticsanches.kge.renderer.gl.GLuint
import org.lwjgl.opengl.GL33

actual val originalGLServiceImplementation: GLService
    get() = DefaultGLService

private data object DefaultGLService : GLService {
    // Texture

    override fun createTexture(): GLTexture = GL33.glGenTextures()

    override fun deleteTexture(texture: GLTexture) = GL33.glDeleteTextures(texture)

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) = GL33.glBindTexture(target, texture ?: 0)

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
        srcData: ByteBuffer,
    ) = GL33.glTexImage2D(target, level, internalFormat, width, height, border, format, type, srcData)

    override fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) = GL33.glReadPixels(x, y, width, height, format, type, dstData)

    // Shader

    override fun createShader(type: GLenum): GLShader = GL33.glCreateShader(type)

    override fun deleteShader(shader: GLShader) = GL33.glDeleteShader(shader)

    override fun shaderSource(
        shader: GLShader,
        source: String,
    ) = GL33.glShaderSource(shader, source)

    override fun compileShader(shader: GLShader) {
        GL33.glCompileShader(shader)
        check(GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS) == GL33.GL_TRUE) {
            "Unable to compile $shader: " + GL33.glGetShaderInfoLog(shader)
        }
    }

    // Program

    override fun createProgram(): GLProgram = GL33.glCreateProgram()

    override fun deleteProgram(program: GLProgram) = GL33.glDeleteProgram(program)

    override fun attachShader(
        program: GLProgram,
        shader: GLShader,
    ) = GL33.glAttachShader(program, shader)

    override fun linkProgram(program: GLProgram) {
        GL33.glLinkProgram(program)
        check(GL33.glGetProgrami(program, GL33.GL_LINK_STATUS) == GL33.GL_TRUE) {
            "Unable to compile $program: " + GL33.glGetProgramInfoLog(program)
        }
    }

    override fun useProgram(program: GLProgram?) = GL33.glUseProgram(program ?: 0)

    // Uniform

    override fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation? = GL33.glGetUniformLocation(program, name).takeIf { it != -1 }

    override fun uniform1f(
        location: GLUniformLocation,
        x: GLfloat,
    ) = GL33.glUniform1f(location, x)

    override fun uniform1fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = GL33.glUniform1fv(location, value)

    override fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    ) = GL33.glUniform1i(location, x)

    override fun uniform1iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = GL33.glUniform1iv(location, value)

    override fun uniform2f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
    ) = GL33.glUniform2f(location, x, y)

    override fun uniform2fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = GL33.glUniform2fv(location, value)

    override fun uniform2i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
    ) = GL33.glUniform2i(location, x, y)

    override fun uniform2iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = GL33.glUniform2iv(location, value)

    override fun uniform3f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
    ) = GL33.glUniform3f(location, x, y, z)

    override fun uniform3fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = GL33.glUniform3fv(location, value)

    override fun uniform3i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
        z: GLint,
    ) = GL33.glUniform3i(location, x, y, z)

    override fun uniform3iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = GL33.glUniform3iv(location, value)

    override fun uniform4f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
        w: GLfloat,
    ) = GL33.glUniform4f(location, x, y, z, w)

    override fun uniform4fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = GL33.glUniform4fv(location, value)

    override fun uniform4i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
        z: GLint,
        w: GLint,
    ) = GL33.glUniform4i(location, x, y, z, w)

    override fun uniform4iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = GL33.glUniform4iv(location, value)

    override fun uniformMatrix2fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    ) = GL33.glUniformMatrix2fv(location, transpose, value)

    override fun uniformMatrix3fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    ) = GL33.glUniformMatrix3fv(location, transpose, value)

    override fun uniformMatrix4fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    ) = GL33.glUniformMatrix4fv(location, transpose, value)

    // Attribute

    override fun getAttribLocation(
        program: GLProgram,
        name: String,
    ): GLint? = GL33.glGetAttribLocation(program, name).takeIf { it != -1 }

    override fun bindAttribLocation(
        program: GLProgram,
        index: GLuint,
        name: String,
    ) = GL33.glBindAttribLocation(program, index, name)

    override fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    ) = GL33.glVertexAttribPointer(index, size, type, normalized, stride, offset.toLong())

    override fun enableVertexAttribArray(index: GLuint) = GL33.glEnableVertexAttribArray(index)

    override fun disableVertexAttribArray(index: GLuint) = GL33.glDisableVertexAttribArray(index)

    override fun vertexAttrib1f(
        index: GLuint,
        x: GLfloat,
    ) = GL33.glVertexAttrib1f(index, x)

    override fun vertexAttrib1fv(
        index: GLuint,
        value: FloatBuffer,
    ) = GL33.glVertexAttrib1fv(index, value)

    override fun vertexAttrib2f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
    ) = GL33.glVertexAttrib2f(index, x, y)

    override fun vertexAttrib2fv(
        index: GLuint,
        value: FloatBuffer,
    ) = GL33.glVertexAttrib2fv(index, value)

    override fun vertexAttrib3f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
    ) = GL33.glVertexAttrib3f(index, x, y, z)

    override fun vertexAttrib3fv(
        index: GLuint,
        value: FloatBuffer,
    ) = GL33.glVertexAttrib3fv(index, value)

    override fun vertexAttrib4f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
        w: GLfloat,
    ) = GL33.glVertexAttrib4f(index, x, y, z, w)

    override fun vertexAttrib4fv(
        index: GLuint,
        value: FloatBuffer,
    ) = GL33.glVertexAttrib4fv(index, value)

    // VAO

    override fun createVertexArray(): GLVertexArrayObject = GL33.glGenVertexArrays()

    override fun deleteVertexArray(vao: GLVertexArrayObject) = GL33.glDeleteVertexArrays(vao)

    override fun bindVertexArray(vao: GLVertexArrayObject?) = GL33.glBindVertexArray(vao ?: 0)

    // Buffer

    override fun createBuffer(): GLBuffer = GL33.glGenBuffers()

    override fun deleteBuffer(buffer: GLBuffer) = GL33.glDeleteBuffers(buffer)

    override fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    ) = GL33.glBindBuffer(target, buffer ?: 0)

    override fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        usage: GLenum,
    ) = GL33.glBufferData(target, srcData, usage)

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

    // Draw arrays

    override fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    ) = GL33.glDrawArrays(mode, first, count)

    override fun multiDrawArrays(
        mode: GLenum,
        first: IntBuffer,
        count: IntBuffer,
    ) = GL33.glMultiDrawArrays(mode, first, count)

    // Capabilities

    override fun enable(cap: GLenum) = GL33.glEnable(cap)

    override fun disable(cap: GLenum) = GL33.glDisable(cap)

    // Blend functions

    override fun blendFunc(
        src: GLenum,
        dst: GLenum,
    ) = GL33.glBlendFunc(src, dst)

    // Clear functions

    override fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    ) = GL33.glClearColor(red, green, blue, alpha)

    override fun clear(mask: GLbitfield) = GL33.glClear(mask)

    // Viewport

    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) = GL33.glViewport(x, y, width, height)

    // Depth buffer test

    override fun depthFunc(func: GLenum) = GL33.glDepthFunc(func)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
