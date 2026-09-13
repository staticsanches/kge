package dev.staticsanches.kge.renderer.gl.service

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.overridable.KGEOverridable
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

/**
 * The raw GL command seam: a thin, near 1:1 wrapper over the platform GL API.
 *
 * Commands take `GLenum` integers and platform handles. The default backend is
 * the real platform API (LWJGL `GL33` on the JVM, WebGL2 on the web); a
 * consumer may replace the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override]. The `create*` methods return a
 * handle the caller owns and must release through the matching `delete*`.
 *
 * Compile and link failures are detected inside the backend, which throws with
 * the driver's info log; [getUniformLocation] normalizes the backend's "no such
 * uniform" sentinel to `null`.
 */
interface GLService : KGEOverridable {
    // Texture

    /** Creates a texture and returns its name. */
    fun createTexture(): GLTexture

    /** Deletes [texture] — the caller must not use it afterwards. */
    fun deleteTexture(texture: GLTexture)

    /** Binds [texture] to [target], or unbinds [target] when [texture] is `null`. */
    fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    )

    /** Sets the integer texture parameter [pname] of [target] to [param]. */
    fun texParameteri(
        target: GLenum,
        pname: GLenum,
        param: GLenum,
    )

    /** Uploads a [width]x[height] image into [target]; [srcData] is `null` for an uninitialized image. */
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

    /** Replaces the [width]x[height] sub-region at ([xOffset], [yOffset]) of [target] with the pixels in [srcData]. */
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

    /** Reads a [width]x[height] pixel block at ([x], [y]) into [dstData]. */
    fun readPixels(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    )

    /**
     * Reads the whole [width]x[height] image at [level] of the texture bound to
     * [target] into [dstData]. The web backend needs [width]/[height] because it
     * has no `glGetTexImage`.
     */
    fun getTexImage(
        target: GLenum,
        level: GLint,
        width: GLsizei,
        height: GLsizei,
        format: GLenum,
        type: GLenum,
        dstData: ByteBuffer,
    )

    // Shader

    /** Creates a shader of [type] (`VERTEX_SHADER`/`FRAGMENT_SHADER`). */
    fun createShader(type: GLenum): GLShader

    /** Deletes [shader]. */
    fun deleteShader(shader: GLShader)

    /** Replaces the source of [shader] with [source]. */
    fun shaderSource(
        shader: GLShader,
        source: String,
    )

    /** Compiles [shader]; throws with the info log when compilation fails. */
    fun compileShader(shader: GLShader)

    // Program

    /** Creates an empty program. */
    fun createProgram(): GLProgram

    /** Deletes [program]. */
    fun deleteProgram(program: GLProgram)

    /** Attaches [shader] to [program]. */
    fun attachShader(
        program: GLProgram,
        shader: GLShader,
    )

    /** Links [program]; throws with the info log when linking fails. */
    fun linkProgram(program: GLProgram)

    /** Makes [program] current, or clears the current program when [program] is `null`. */
    fun useProgram(program: GLProgram?)

    // Uniform

    /** The location of uniform [name] in [program], or `null` when it is inactive. */
    fun getUniformLocation(
        program: GLProgram,
        name: String,
    ): GLUniformLocation?

    /** Sets the sampler/int uniform at [location] to [x]. */
    fun uniform1i(
        location: GLUniformLocation,
        x: GLint,
    )

    // Buffer

    /** Creates a buffer object. */
    fun createBuffer(): GLBuffer

    /** Deletes [buffer]. */
    fun deleteBuffer(buffer: GLBuffer)

    /** Binds [buffer] to [target], or unbinds [target] when [buffer] is `null`. */
    fun bindBuffer(
        target: GLenum,
        buffer: GLBuffer?,
    )

    /**
     * Orphan-uploads the first [byteCount] bytes of [srcData] into [target],
     * re-specifying the storage to exactly that size so a queued draw cannot
     * reference overwritten storage. [byteCount] must be within
     * `0..srcData.capacity()`.
     */
    fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        byteCount: GLsizeiptr,
        usage: GLenum,
    )

    /** Allocates [size] bytes of [target] storage with the given [usage] hint. */
    fun bufferData(
        target: GLenum,
        size: GLsizeiptr,
        usage: GLenum,
    )

    /** Replaces the [target] storage starting at [dstByteOffset] with [srcData]. */
    fun bufferSubData(
        target: GLenum,
        dstByteOffset: GLintptr,
        srcData: ByteBuffer,
    )

    // Vertex array object

    /** Creates a vertex array object. */
    fun createVertexArray(): GLVertexArrayObject

    /** Deletes [vao]. */
    fun deleteVertexArray(vao: GLVertexArrayObject)

    /** Binds [vao], or unbinds the current one when [vao] is `null`. */
    fun bindVertexArray(vao: GLVertexArrayObject?)

    // Attribute

    /** Declares the layout of vertex attribute [index] in the currently bound array buffer. */
    fun vertexAttribPointer(
        index: GLuint,
        size: GLint,
        type: GLenum,
        normalized: GLboolean,
        stride: GLsizei,
        offset: GLintptr,
    )

    /** Enables vertex attribute array [index]. */
    fun enableVertexAttribArray(index: GLuint)

    // Draw

    /** Draws [count] vertices of [mode] starting at [first]. */
    fun drawArrays(
        mode: GLenum,
        first: GLint,
        count: GLsizei,
    )

    // State

    /** Enables the capability [cap]. */
    fun enable(cap: GLenum)

    /** Disables the capability [cap]. */
    fun disable(cap: GLenum)

    /** Sets the blend factors used when blending is enabled. */
    fun blendFunc(
        src: GLenum,
        dst: GLenum,
    )

    /** Sets the color used by [clear]. */
    fun clearColor(
        red: GLclampf,
        green: GLclampf,
        blue: GLclampf,
        alpha: GLclampf,
    )

    /** Clears the buffers selected by [mask]. */
    fun clear(mask: GLbitfield)

    /** Sets the drawable viewport rectangle. */
    fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    )

    companion object :
        KGEOverridable.Proxy<GLService>(GLService::class, glServiceDefault),
        GLService {
        override fun createTexture(): GLTexture = delegate.createTexture()

        override fun deleteTexture(texture: GLTexture) = delegate.deleteTexture(texture)

        override fun bindTexture(
            target: GLenum,
            texture: GLTexture?,
        ) = delegate.bindTexture(target, texture)

        override fun texParameteri(
            target: GLenum,
            pname: GLenum,
            param: GLenum,
        ) = delegate.texParameteri(target, pname, param)

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
        ) = delegate.texImage2D(target, level, internalFormat, width, height, border, format, type, srcData)

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
        ) = delegate.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, srcData)

        override fun readPixels(
            x: GLint,
            y: GLint,
            width: GLsizei,
            height: GLsizei,
            format: GLenum,
            type: GLenum,
            dstData: ByteBuffer,
        ) = delegate.readPixels(x, y, width, height, format, type, dstData)

        override fun getTexImage(
            target: GLenum,
            level: GLint,
            width: GLsizei,
            height: GLsizei,
            format: GLenum,
            type: GLenum,
            dstData: ByteBuffer,
        ) = delegate.getTexImage(target, level, width, height, format, type, dstData)

        override fun createShader(type: GLenum): GLShader = delegate.createShader(type)

        override fun deleteShader(shader: GLShader) = delegate.deleteShader(shader)

        override fun shaderSource(
            shader: GLShader,
            source: String,
        ) = delegate.shaderSource(shader, source)

        override fun compileShader(shader: GLShader) = delegate.compileShader(shader)

        override fun createProgram(): GLProgram = delegate.createProgram()

        override fun deleteProgram(program: GLProgram) = delegate.deleteProgram(program)

        override fun attachShader(
            program: GLProgram,
            shader: GLShader,
        ) = delegate.attachShader(program, shader)

        override fun linkProgram(program: GLProgram) = delegate.linkProgram(program)

        override fun useProgram(program: GLProgram?) = delegate.useProgram(program)

        override fun getUniformLocation(
            program: GLProgram,
            name: String,
        ): GLUniformLocation? = delegate.getUniformLocation(program, name)

        override fun uniform1i(
            location: GLUniformLocation,
            x: GLint,
        ) = delegate.uniform1i(location, x)

        override fun createBuffer(): GLBuffer = delegate.createBuffer()

        override fun deleteBuffer(buffer: GLBuffer) = delegate.deleteBuffer(buffer)

        override fun bindBuffer(
            target: GLenum,
            buffer: GLBuffer?,
        ) = delegate.bindBuffer(target, buffer)

        override fun bufferData(
            target: GLenum,
            srcData: ByteBuffer,
            byteCount: GLsizeiptr,
            usage: GLenum,
        ) = delegate.bufferData(target, srcData, byteCount, usage)

        override fun bufferData(
            target: GLenum,
            size: GLsizeiptr,
            usage: GLenum,
        ) = delegate.bufferData(target, size, usage)

        override fun bufferSubData(
            target: GLenum,
            dstByteOffset: GLintptr,
            srcData: ByteBuffer,
        ) = delegate.bufferSubData(target, dstByteOffset, srcData)

        override fun createVertexArray(): GLVertexArrayObject = delegate.createVertexArray()

        override fun deleteVertexArray(vao: GLVertexArrayObject) = delegate.deleteVertexArray(vao)

        override fun bindVertexArray(vao: GLVertexArrayObject?) = delegate.bindVertexArray(vao)

        override fun vertexAttribPointer(
            index: GLuint,
            size: GLint,
            type: GLenum,
            normalized: GLboolean,
            stride: GLsizei,
            offset: GLintptr,
        ) = delegate.vertexAttribPointer(index, size, type, normalized, stride, offset)

        override fun enableVertexAttribArray(index: GLuint) = delegate.enableVertexAttribArray(index)

        override fun drawArrays(
            mode: GLenum,
            first: GLint,
            count: GLsizei,
        ) = delegate.drawArrays(mode, first, count)

        override fun enable(cap: GLenum) = delegate.enable(cap)

        override fun disable(cap: GLenum) = delegate.disable(cap)

        override fun blendFunc(
            src: GLenum,
            dst: GLenum,
        ) = delegate.blendFunc(src, dst)

        override fun clearColor(
            red: GLclampf,
            green: GLclampf,
            blue: GLclampf,
            alpha: GLclampf,
        ) = delegate.clearColor(red, green, blue, alpha)

        override fun clear(mask: GLbitfield) = delegate.clear(mask)

        override fun viewport(
            x: GLint,
            y: GLint,
            width: GLsizei,
            height: GLsizei,
        ) = delegate.viewport(x, y, width, height)
    }
}

internal expect val glServiceDefault: GLService
