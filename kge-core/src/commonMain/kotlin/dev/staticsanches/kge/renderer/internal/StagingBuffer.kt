package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLBuffer
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * The renderer's dynamic CPU staging storage and its GPU vertex buffer.
 *
 * The GL buffer is created once and kept for the renderer's lifetime (its VAO
 * attribute bindings must stay valid); the CPU buffer is reallocated only when a
 * request no longer fits, so repeated draws of the same size reuse it. Each draw
 * orphan-uploads exactly the bytes it wrote (see [upload]).
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
     * the CPU buffer to write into. A storage that already fits is reused.
     */
    fun ensureCapacity(byteCount: Int): ResourceWrapper<ByteBuffer> {
        require(byteCount >= 0) { "byteCount must be >= 0: $byteCount" }
        data?.let { current -> if (capacityBytes >= byteCount) return current }

        val grown = BufferService.allocate(byteCount, "$name staging")
        val previous = data
        data = grown
        capacityBytes = byteCount
        previous?.close()
        return grown
    }

    /**
     * Orphan-uploads the first [byteCount] bytes of the current staging storage
     * into the GPU vertex buffer as [GL.STREAM_DRAW]. The buffer storage is
     * re-specified to exactly [byteCount] bytes, so a queued draw can never
     * keep referencing bytes a later upload overwrites.
     *
     * [ensureCapacity] must have been called and [byteCount] must be within
     * `0..capacityBytes`; otherwise this fails fast.
     */
    fun upload(byteCount: Int) {
        require(byteCount in 0..capacityBytes) {
            "byteCount must be within 0..$capacityBytes: $byteCount"
        }
        val current = checkNotNull(data) { "upload before ensureCapacity" }
        GL.bindBuffer(GL.ARRAY_BUFFER, vertexBuffer.resource)
        GL.bufferData(GL.ARRAY_BUFFER, current.resource, byteCount, GL.STREAM_DRAW)
    }

    override fun close() {
        val current = data
        data = null
        capacityBytes = 0
        current?.close()
        vertexBuffer.close()
    }
}
