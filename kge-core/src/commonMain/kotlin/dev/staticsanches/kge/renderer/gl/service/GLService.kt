package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
import dev.staticsanches.kge.extensible.KGEExtensibleService
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

interface GLService : KGEExtensibleService {
    // Texture

    fun createTexture(): GLTexture

    fun deleteTexture(texture: GLTexture)

    fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    )

    fun texParameteri(
        target: GLenum,
        pname: GLenum,
        param: GLenum,
    )

    fun texImage2D(
        target: GLenum,
        level: GLint,
        internalFormat: GLenum,
        width: GLsizei,
        height: GLsizei,
        border: GLint,
        format: GLenum,
        type: GLenum,
        srcData: ByteBuffer?,
    )

    fun texSubImage2D(
        target: GLenum,
        level: GLint,
        xOffset: GLint,
        yOffset: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        srcData: ByteBuffer,
    )

    fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    )

    // Shader

    fun createShader(type: GLenum): GLShader

    fun deleteShader(shader: GLShader)

    fun shaderSource(
        shader: GLShader,
        source: String,
    )

    fun compileShader(shader: GLShader)

    // Program

    fun createProgram(): GLProgram

    fun deleteProgram(program: GLProgram)

    fun attachShader(
        program: GLProgram,
        shader: GLShader,
    )

    fun linkProgram(program: GLProgram)

    fun useProgram(program: GLProgram?)

    // Uniform

    fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation?

    fun uniform1f(
        location: GLUniformLocation,
        x: GLfloat,
    )

    fun uniform1fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    )

    fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    )

    fun uniform1iv(
        location: GLUniformLocation,
        value: IntBuffer,
    )

    fun uniform2f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
    )

    fun uniform2fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    )

    fun uniform2i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
    )

    fun uniform2iv(
        location: GLUniformLocation,
        value: IntBuffer,
    )

    fun uniform3f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
    )

    fun uniform3fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    )

    fun uniform3i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
        z: GLint,
    )

    fun uniform3iv(
        location: GLUniformLocation,
        value: IntBuffer,
    )

    fun uniform4f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
        w: GLfloat,
    )

    fun uniform4fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    )

    fun uniform4i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
        z: GLint,
        w: GLint,
    )

    fun uniform4iv(
        location: GLUniformLocation,
        value: IntBuffer,
    )

    fun uniformMatrix2fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    )

    fun uniformMatrix3fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    )

    fun uniformMatrix4fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    )

    // Attribute

    fun getAttribLocation(
        program: GLProgram,
        name: String,
    ): GLint?

    fun bindAttribLocation(
        program: GLProgram,
        index: GLuint,
        name: String,
    )

    fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    )

    fun enableVertexAttribArray(index: GLuint)

    fun disableVertexAttribArray(index: GLuint)

    fun vertexAttrib1f(
        index: GLuint,
        x: GLfloat,
    )

    fun vertexAttrib1fv(
        index: GLuint,
        value: FloatBuffer,
    )

    fun vertexAttrib2f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
    )

    fun vertexAttrib2fv(
        index: GLuint,
        value: FloatBuffer,
    )

    fun vertexAttrib3f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
    )

    fun vertexAttrib3fv(
        index: GLuint,
        value: FloatBuffer,
    )

    fun vertexAttrib4f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
        w: GLfloat,
    )

    fun vertexAttrib4fv(
        index: GLuint,
        value: FloatBuffer,
    )

    // VAO

    fun createVertexArray(): GLVertexArrayObject

    fun deleteVertexArray(vao: GLVertexArrayObject)

    fun bindVertexArray(vao: GLVertexArrayObject?)

    // Buffer

    fun createBuffer(): GLBuffer

    fun deleteBuffer(buffer: GLBuffer)

    fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    )

    fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        usage: GLenum,
    )

    fun bufferData(
        target: GLenum,
        size: GLsizeiptr,
        usage: GLenum,
    )

    fun bufferSubData(
        target: GLenum,
        dstByteOffset: GLintptr,
        srcData: ByteBuffer,
    )

    // Draw arrays

    fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    )

    fun multiDrawArrays(
        mode: GLenum,
        first: IntBuffer,
        count: IntBuffer,
    )

    // Capabilities

    fun enable(cap: GLenum)

    fun disable(cap: GLenum)

    // Blend functions

    fun blendFunc(
        src: GLenum,
        dst: GLenum,
    )

    // Clear functions

    fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    )

    fun clear(mask: GLbitfield)

    // Viewport

    fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    )

    // Depth buffer test

    fun depthFunc(func: GLenum)

    companion object : GLService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalGLServiceImplementation
}

expect val originalGLServiceImplementation: GLService
