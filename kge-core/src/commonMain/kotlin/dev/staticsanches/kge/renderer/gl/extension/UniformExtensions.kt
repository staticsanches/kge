@file:Suppress("unused")

package dev.staticsanches.kge.renderer.gl.extension

import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.buffer.IntBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLboolean
import dev.staticsanches.kge.renderer.gl.GLfloat
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.wrapper.GLProgramWrapper

fun GLProgramWrapper.uniform1f(
    name: String,
    x: GLfloat,
) = GL.uniform1f(getUniformLocation(name), x)

fun GLProgramWrapper.uniform1fv(
    name: String,
    value: FloatBuffer,
) = GL.uniform1fv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform1i(
    name: String,
    x: GLint,
) = GL.uniform1i(getUniformLocation(name), x)

fun GLProgramWrapper.uniform1iv(
    name: String,
    value: IntBuffer,
) = GL.uniform1iv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform2f(
    name: String,
    x: GLfloat,
    y: GLfloat,
) = GL.uniform2f(getUniformLocation(name), x, y)

fun GLProgramWrapper.uniform2fv(
    name: String,
    value: FloatBuffer,
) = GL.uniform2fv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform2i(
    name: String,
    x: GLint,
    y: GLint,
) = GL.uniform2i(getUniformLocation(name), x, y)

fun GLProgramWrapper.uniform2iv(
    name: String,
    value: IntBuffer,
) = GL.uniform2iv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform3f(
    name: String,
    x: GLfloat,
    y: GLfloat,
    z: GLfloat,
) = GL.uniform3f(getUniformLocation(name), x, y, z)

fun GLProgramWrapper.uniform3fv(
    name: String,
    value: FloatBuffer,
) = GL.uniform3fv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform3i(
    name: String,
    x: GLint,
    y: GLint,
    z: GLint,
) = GL.uniform3i(getUniformLocation(name), x, y, z)

fun GLProgramWrapper.uniform3iv(
    name: String,
    value: IntBuffer,
) = GL.uniform3iv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform4f(
    name: String,
    x: GLfloat,
    y: GLfloat,
    z: GLfloat,
    w: GLfloat,
) = GL.uniform4f(getUniformLocation(name), x, y, z, w)

fun GLProgramWrapper.uniform4fv(
    name: String,
    value: FloatBuffer,
) = GL.uniform4fv(getUniformLocation(name), value)

fun GLProgramWrapper.uniform4i(
    name: String,
    x: GLint,
    y: GLint,
    z: GLint,
    w: GLint,
) = GL.uniform4i(getUniformLocation(name), x, y, z, w)

fun GLProgramWrapper.uniform4iv(
    name: String,
    value: IntBuffer,
) = GL.uniform4iv(getUniformLocation(name), value)

fun GLProgramWrapper.uniformMatrix2fv(
    name: String,
    transpose: GLboolean,
    value: FloatBuffer,
) = GL.uniformMatrix2fv(getUniformLocation(name), transpose, value)

fun GLProgramWrapper.uniformMatrix3fv(
    name: String,
    transpose: GLboolean,
    value: FloatBuffer,
) = GL.uniformMatrix3fv(getUniformLocation(name), transpose, value)

fun GLProgramWrapper.uniformMatrix4fv(
    name: String,
    transpose: GLboolean,
    value: FloatBuffer,
) = GL.uniformMatrix4fv(getUniformLocation(name), transpose, value)
