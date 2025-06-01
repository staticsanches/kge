@file:Suppress("unused")

package dev.staticsanches.kge.renderer.gl.extension

import dev.staticsanches.kge.buffer.FloatBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLboolean
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLfloat
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLintptr
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.wrapper.GLProgramWrapper

fun GLProgramWrapper.vertexAttribPointer(
    name: String,
    size: GLint,
    type: GLenum,
    normalized: GLboolean,
    stride: GLsizei,
    offset: GLintptr,
) = GL.vertexAttribPointer(getAttribLocation(name), size, type, normalized, stride, offset)

fun GLProgramWrapper.enableVertexAttribArray(name: String) = GL.enableVertexAttribArray(getAttribLocation(name))

fun GLProgramWrapper.disableVertexAttribArray(name: String) = GL.disableVertexAttribArray(getAttribLocation(name))

fun GLProgramWrapper.vertexAttrib1f(
    name: String,
    x: GLfloat,
) = GL.vertexAttrib1f(getAttribLocation(name), x)

fun GLProgramWrapper.vertexAttrib1fv(
    name: String,
    value: FloatBuffer,
) = GL.vertexAttrib1fv(getAttribLocation(name), value)

fun GLProgramWrapper.vertexAttrib2f(
    name: String,
    x: GLfloat,
    y: GLfloat,
) = GL.vertexAttrib2f(getAttribLocation(name), x, y)

fun GLProgramWrapper.vertexAttrib2fv(
    name: String,
    value: FloatBuffer,
) = GL.vertexAttrib2fv(getAttribLocation(name), value)

fun GLProgramWrapper.vertexAttrib3f(
    name: String,
    x: GLfloat,
    y: GLfloat,
    z: GLfloat,
) = GL.vertexAttrib3f(getAttribLocation(name), x, y, z)

fun GLProgramWrapper.vertexAttrib3fv(
    name: String,
    value: FloatBuffer,
) = GL.vertexAttrib3fv(getAttribLocation(name), value)

fun GLProgramWrapper.vertexAttrib4f(
    name: String,
    x: GLfloat,
    y: GLfloat,
    z: GLfloat,
    w: GLfloat,
) = GL.vertexAttrib4f(getAttribLocation(name), x, y, z, w)

fun GLProgramWrapper.vertexAttrib4fv(
    name: String,
    value: FloatBuffer,
) = GL.vertexAttrib4fv(getAttribLocation(name), value)
