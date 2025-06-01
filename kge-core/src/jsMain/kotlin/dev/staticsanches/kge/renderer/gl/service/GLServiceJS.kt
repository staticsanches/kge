package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
import dev.staticsanches.kge.renderer.gl.GL
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
import dev.staticsanches.kge.renderer.gl.gl
import dev.staticsanches.kge.renderer.gl.glMultiDraw
import web.gl.WebGL2RenderingContext

actual val originalGLServiceImplementation: GLService
    get() = DefaultGLService

private data object DefaultGLService : GLService {
    // Texture

    override fun createTexture(): GLTexture = gl.createTexture()

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
        srcData: ByteBuffer,
    ) {
        check(type == GL.UNSIGNED_BYTE) { "Invalid type: $type" }
        gl.texImage2D(
            target.asGLenum(),
            level,
            internalFormat.asGLenum(),
            width,
            height,
            border,
            format.asGLenum(),
            type.asGLenum(),
            srcData.asUint8Array(),
            0,
        )
    }

    override fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    ) {
        check(type == GL.UNSIGNED_BYTE) { "Invalid type: $type" }
        gl.readPixels(
            x, y,
            width, height,
            format.asGLenum(),
            type.asGLenum(),
            dstData.asUint8Array(),
        )
    }

    // Shader

    override fun createShader(type: GLenum): GLShader =
        checkNotNull(gl.createShader(type.asGLenum())) { "Unable to create shader" }

    override fun deleteShader(shader: GLShader) = gl.deleteShader(shader)

    override fun shaderSource(
        shader: GLShader,
        source: String,
    ) = gl.shaderSource(shader, source)

    override fun compileShader(shader: GLShader) {
        gl.compileShader(shader)
        check(gl.getShaderParameter(shader, WebGL2RenderingContext.COMPILE_STATUS) == true) {
            "Unable to compile $shader: " + gl.getShaderInfoLog(shader)
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
        check(gl.getProgramParameter(program, WebGL2RenderingContext.LINK_STATUS) == true) {
            "Unable to compile $program: " + gl.getProgramInfoLog(program)
        }
    }

    override fun useProgram(program: GLProgram?) = gl.useProgram(program)

    // Uniform

    override fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation? = gl.getUniformLocation(program, name)

    override fun uniform1f(
        location: GLUniformLocation,
        x: GLfloat,
    ) = gl.uniform1f(location, x)

    override fun uniform1fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = gl.uniform1fv(location, value.asFloat32Array(), undefined, undefined)

    override fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    ) = gl.uniform1i(location, x)

    override fun uniform1iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = gl.uniform1iv(location, value.asInt32Array(), undefined, undefined)

    override fun uniform2f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
    ) = gl.uniform2f(location, x, y)

    override fun uniform2fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = gl.uniform2fv(location, value.asFloat32Array(), undefined, undefined)

    override fun uniform2i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
    ) = gl.uniform2i(location, x, y)

    override fun uniform2iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = gl.uniform2iv(location, value.asInt32Array(), undefined, undefined)

    override fun uniform3f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
    ) = gl.uniform3f(location, x, y, z)

    override fun uniform3fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = gl.uniform3fv(location, value.asFloat32Array(), undefined, undefined)

    override fun uniform3i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
        z: GLint,
    ) = gl.uniform3i(location, x, y, z)

    override fun uniform3iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = gl.uniform3iv(location, value.asInt32Array(), undefined, undefined)

    override fun uniform4f(
        location: GLUniformLocation,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
        w: GLfloat,
    ) = gl.uniform4f(location, x, y, z, w)

    override fun uniform4fv(
        location: GLUniformLocation,
        value: FloatBuffer,
    ) = gl.uniform4fv(location, value.asFloat32Array(), undefined, undefined)

    override fun uniform4i(
        location: GLUniformLocation,
        x: GLint,
        y: GLint,
        z: GLint,
        w: GLint,
    ) = gl.uniform4i(location, x, y, z, w)

    override fun uniform4iv(
        location: GLUniformLocation,
        value: IntBuffer,
    ) = gl.uniform4iv(location, value.asInt32Array(), undefined, undefined)

    override fun uniformMatrix2fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    ) = gl.uniformMatrix2fv(location, transpose, value.asFloat32Array(), undefined, undefined)

    override fun uniformMatrix3fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    ) = gl.uniformMatrix3fv(location, transpose, value.asFloat32Array(), undefined, undefined)

    override fun uniformMatrix4fv(
        location: GLUniformLocation,
        transpose: GLboolean,
        value: FloatBuffer,
    ) = gl.uniformMatrix4fv(location, transpose, value.asFloat32Array(), undefined, undefined)

    // Attribute

    override fun getAttribLocation(
        program: GLProgram,
        name: String,
    ): GLint? = gl.getAttribLocation(program, name).takeIf { it != -1 }

    override fun bindAttribLocation(
        program: GLProgram,
        index: GLuint,
        name: String,
    ) = gl.bindAttribLocation(program, index, name)

    override fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    ) = gl.vertexAttribPointer(index, size, type.asGLenum(), normalized, stride, offset)

    override fun enableVertexAttribArray(index: GLuint) = gl.enableVertexAttribArray(index)

    override fun disableVertexAttribArray(index: GLuint) = gl.disableVertexAttribArray(index)

    override fun vertexAttrib1f(
        index: GLuint,
        x: GLfloat,
    ) = gl.vertexAttrib1f(index, x)

    override fun vertexAttrib1fv(
        index: GLuint,
        value: FloatBuffer,
    ) = gl.vertexAttrib1fv(index, value.asFloat32Array())

    override fun vertexAttrib2f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
    ) = gl.vertexAttrib2f(index, x, y)

    override fun vertexAttrib2fv(
        index: GLuint,
        value: FloatBuffer,
    ) = gl.vertexAttrib2fv(index, value.asFloat32Array())

    override fun vertexAttrib3f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
    ) = gl.vertexAttrib3f(index, x, y, z)

    override fun vertexAttrib3fv(
        index: GLuint,
        value: FloatBuffer,
    ) = gl.vertexAttrib3fv(index, value.asFloat32Array())

    override fun vertexAttrib4f(
        index: GLuint,
        x: GLfloat,
        y: GLfloat,
        z: GLfloat,
        w: GLfloat,
    ) = gl.vertexAttrib4f(index, x, y, z, w)

    override fun vertexAttrib4fv(
        index: GLuint,
        value: FloatBuffer,
    ) = gl.vertexAttrib4fv(index, value.asFloat32Array())

    // VAO

    override fun createVertexArray(): GLVertexArrayObject = gl.createVertexArray()

    override fun deleteVertexArray(vao: GLVertexArrayObject) = gl.deleteVertexArray(vao)

    override fun bindVertexArray(vao: GLVertexArrayObject?) = gl.bindVertexArray(vao)

    // Buffer

    override fun createBuffer(): GLBuffer = gl.createBuffer()

    override fun deleteBuffer(buffer: GLBuffer) = gl.deleteBuffer(buffer)

    override fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    ) = gl.bindBuffer(target.asDynamic(), buffer)

    override fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        usage: GLenum,
    ) = gl.bufferData(target.asDynamic(), srcData.asDataView(), usage.asDynamic())

    override fun bufferData(
        target: GLenum,
        size: GLsizeiptr,
        usage: GLenum,
    ) = gl.bufferData(target.asDynamic(), size, usage.asDynamic())

    override fun bufferSubData(
        target: GLenum,
        dstByteOffset: GLintptr,
        srcData: ByteBuffer,
    ) = gl.bufferSubData(target.asDynamic(), dstByteOffset, srcData.asDataView())

    // Draw arrays

    override fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    ) = gl.drawArrays(mode.asGLenum(), first, count)

    override fun multiDrawArrays(
        mode: GLenum,
        first: IntBuffer,
        count: IntBuffer,
    ) = glMultiDraw.multiDrawArraysWEBGL(
        mode.asGLenum(),
        first.asInt32Array(), 0,
        count.asInt32Array(), 0,
        first.remaining(),
    )

    // Capabilities

    override fun enable(cap: GLenum) = gl.enable(cap.asGLenum())

    override fun disable(cap: GLenum) = gl.disable(cap.asGLenum())

    // Blend functions

    override fun blendFunc(
        src: GLenum,
        dst: GLenum,
    ) = gl.blendFunc(src.asGLenum(), dst.asGLenum())

    // Clear functions

    override fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    ) = gl.clearColor(red, green, blue, alpha)

    override fun clear(mask: GLbitfield) = gl.clear(mask.unsafeCast<web.gl.GLbitfield>())

    // Viewport

    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) = gl.viewport(x, y, width, height)

    // Depth buffer test

    override fun depthFunc(func: GLenum) = gl.depthFunc(func.asGLenum())

    private fun GLenum.asGLenum(): web.gl.GLenum = unsafeCast<web.gl.GLenum>()

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
