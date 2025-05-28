package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei

interface GLService : KGEExtensibleService {
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

    companion object : GLService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalGLServiceImplementation
}

expect val originalGLServiceImplementation: GLService
