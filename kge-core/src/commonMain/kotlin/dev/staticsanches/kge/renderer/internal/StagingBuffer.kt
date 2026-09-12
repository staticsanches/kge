package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The renderer's dynamic CPU staging storage and its GPU vertex buffer.
 *
 * The GL buffer is created once and kept for the renderer's lifetime (its VAO
 * attribute bindings must stay valid); the CPU buffer is reallocated and the
 * GPU storage re-specified only when a request no longer fits, so repeated
 * draws of the same size reuse both. There is no global capacity configuration.
 */
internal class StagingBuffer(
    private val name: String,
) : AutoCloseable {
    private val vertexBuffer: ResourceWrapper<GLBuffer> = createBufferResource(name)
    private var data: ResourceWrapper<ByteBuffer>? = null
    private var capacityBytes = 0

    /** The GPU buffer the VAO's attribute bindings point at. */
    val buffer: GLBuffer get() = vertexBuffer.resource

    /**
     * Ensures the staging storage holds at least [byteCount] bytes and returns
     * the CPU buffer to write into. Storage that already fits is reused
     * unchanged; otherwise the CPU buffer is reallocated and the GPU storage
     * re-specified with [GL.DYNAMIC_DRAW]. A failure while growing keeps the
     * previous storage intact and frees the fresh allocation.
     */
    fun ensureCapacity(byteCount: Int): ResourceWrapper<ByteBuffer> {
        require(byteCount >= 0) { "byteCount must be >= 0: $byteCount" }
        data?.let { current -> if (capacityBytes >= byteCount) return current }

        val grown = BufferService.allocate(byteCount, "$name staging")
        grown.letClosingIfFailed { staging ->
            GL.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer.resource)
            GL.bufferData(GL.ARRAY_BUFFER, byteCount, GL.DYNAMIC_DRAW)
            staging
        }

        val previous = data
        data = grown
        capacityBytes = byteCount
        previous?.close()
        return grown
    }

    override fun close() {
        val current = data
        data = null
        capacityBytes = 0
        current?.close()
        vertexBuffer.close()
    }
}
