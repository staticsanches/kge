package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLVertexArrayObject
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.GLsizeiptr
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.KGEResource

/** One vertex of the built-in program's layout: pos4 + uv2 + packed tint. */
private const val VERTEX_BYTES = 28

/** The persistent vertex storage: the measured frames stay far below this. */
private const val VERTEX_CAPACITY = 4 * 1024 * 1024

/**
 * Emulates the renderer's batching levers at the GL boundary (repeated blend, cull
 * and texture state dropped; uploads appended), so it measures a lower bound.
 */
internal class BatchGLCalls(
    private val delegate: GLService,
    private val dedupeBlend: Boolean = false,
    private val dedupeDisable: Boolean = false,
    private val dedupeTexture: Boolean = false,
    appendUploads: Boolean = false,
) : GLService by delegate,
    KGEResource {
    private val vertices: GLBuffer? = if (appendUploads) createVertexBuffer() else null
    private var cullDisabled = false
    private var blendKnown = false
    private var lastBlendSrc: GLenum = 0
    private var lastBlendDst: GLenum = 0
    private var lastTexture: GLTexture? = null
    private var offset = 0
    private var closed = false

    /**
     * Deletes the persistent vertex buffer; idempotent, and using the decorator
     * afterwards fails fast.
     */
    override fun close() {
        if (closed) return
        closed = true
        vertices?.let(delegate::deleteBuffer)
    }

    override fun disable(cap: GLenum) {
        if (dedupeDisable && cap == GL.CULL_FACE) {
            if (cullDisabled) return
            cullDisabled = true
        }
        delegate.disable(cap)
    }

    override fun blendFunc(
        src: GLenum,
        dst: GLenum,
    ) {
        if (dedupeBlend && blendKnown && src == lastBlendSrc && dst == lastBlendDst) return
        blendKnown = true
        lastBlendSrc = src
        lastBlendDst = dst
        delegate.blendFunc(src, dst)
    }

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) {
        if (dedupeTexture && texture != null && texture == lastTexture) return
        lastTexture = texture
        delegate.bindTexture(target, texture)
    }

    override fun viewport(
        x: GLint,
        y: GLint,
        width: GLsizei,
        height: GLsizei,
    ) {
        checkOpen()
        val buffer = vertices
        if (buffer != null) {
            delegate.bindBuffer(GL.ARRAY_BUFFER, buffer)
            delegate.bufferData(GL.ARRAY_BUFFER, VERTEX_CAPACITY, GL.STREAM_DRAW)
            offset = 0
        }
        cullDisabled = false
        blendKnown = false
        lastTexture = null
        delegate.viewport(x, y, width, height)
    }

    override fun bindVertexArray(vao: GLVertexArrayObject?) {
        checkOpen()
        delegate.bindVertexArray(vao)
        val buffer = vertices ?: return
        if (vao == null) return
        // The built-in program's attribute layout, bound against the persistent
        // buffer: pos4 at 0, uv2 at 16, packed tint at 24, 28 bytes per vertex.
        delegate.bindBuffer(GL.ARRAY_BUFFER, buffer)
        delegate.vertexAttribPointer(0, 4, GL.FLOAT, false, VERTEX_BYTES, 0)
        delegate.enableVertexAttribArray(0)
        delegate.vertexAttribPointer(1, 2, GL.FLOAT, false, VERTEX_BYTES, 16)
        delegate.enableVertexAttribArray(1)
        delegate.vertexAttribPointer(2, 4, GL.UNSIGNED_BYTE, true, VERTEX_BYTES, 24)
        delegate.enableVertexAttribArray(2)
    }

    override fun bufferData(
        target: GLenum,
        srcData: ByteBuffer,
        byteCount: GLsizeiptr,
        usage: GLenum,
    ) {
        checkOpen()
        val buffer = vertices
        if (buffer == null || target != GL.ARRAY_BUFFER) {
            delegate.bufferData(target, srcData, byteCount, usage)
            return
        }
        check(offset + byteCount <= VERTEX_CAPACITY) {
            "the benchmark's persistent vertex buffer is too small for $byteCount bytes"
        }
        delegate.bindBuffer(GL.ARRAY_BUFFER, buffer)
        delegate.bufferSubData(GL.ARRAY_BUFFER, offset, srcData)
        offset += byteCount
    }

    /** Creates and orphans the persistent vertex buffer, releasing it on failure. */
    private fun createVertexBuffer(): GLBuffer {
        val buffer = delegate.createBuffer()
        return try {
            delegate.bindBuffer(GL.ARRAY_BUFFER, buffer)
            delegate.bufferData(GL.ARRAY_BUFFER, VERTEX_CAPACITY, GL.STREAM_DRAW)
            buffer
        } catch (failure: Throwable) {
            delegate.deleteBuffer(buffer)
            throw failure
        }
    }

    private fun checkOpen() {
        check(!closed) { "the lever decorator is closed" }
    }
}
