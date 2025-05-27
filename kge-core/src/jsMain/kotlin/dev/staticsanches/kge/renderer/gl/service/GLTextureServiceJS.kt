package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.gl
import js.typedarrays.Uint8Array

actual val originalGLTextureServiceImplementation: GLTextureService
    get() = DefaultGLTextureService

private data object DefaultGLTextureService : GLTextureService {
    override fun createTexture(): GLTexture = gl.createTexture()

    override fun deleteTexture(texture: GLTexture) = gl.deleteTexture(texture)

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) = gl.bindTexture(target.unsafeCast<web.gl.GLenum>(), texture)

    override fun texParameteri(
        target: GLenum,
        pname: GLenum,
        param: GLenum,
    ) = gl.texParameteri(
        target.unsafeCast<web.gl.GLenum>(),
        pname.unsafeCast<web.gl.GLenum>(),
        param.unsafeCast<web.gl.GLenum>(),
    )

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
            target.unsafeCast<web.gl.GLenum>(),
            level,
            internalFormat.unsafeCast<web.gl.GLenum>(),
            width,
            height,
            border,
            format.unsafeCast<web.gl.GLenum>(),
            type.unsafeCast<web.gl.GLenum>(),
            srcData.createView(1, ::Uint8Array),
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
            x,
            y,
            width,
            height,
            format.unsafeCast<web.gl.GLenum>(),
            type.unsafeCast<web.gl.GLenum>(),
            dstData.createView(1, ::Uint8Array),
        )
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
