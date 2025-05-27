package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import org.lwjgl.opengl.GL33

actual val originalGLTextureServiceImplementation: GLTextureService
    get() = DefaultGLTextureService

private data object DefaultGLTextureService : GLTextureService {
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

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
