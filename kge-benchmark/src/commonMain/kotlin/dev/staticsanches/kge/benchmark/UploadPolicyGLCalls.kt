package dev.staticsanches.kge.benchmark

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed

/** The upload work a text cell replays each frame: the recorded boxes or one whole chart. */
internal enum class UploadPolicy {
    /** Re-issues every recorded box, pre-packed at record time. */
    REGION,

    /** Re-issues one whole-chart `texImage2D` per touched chart. */
    FULL,
}

/**
 * A benchmark-only [GLService] decorator that shadows the textures the text
 * carrier seeds, records its region uploads and replays them under [policy].
 */
internal class UploadPolicyGLCalls(
    private val delegate: GLService,
    private val policy: UploadPolicy,
) : GLService by delegate,
    KGEResource {
    private val shadows = mutableMapOf<GLTexture, TextureShadow>()
    private val regions = mutableListOf<RecordedRegion>()
    private val touchedCharts = LinkedHashSet<GLTexture>()
    private var bound: GLTexture? = null
    private var closed = false

    /** The boxes a region replay re-issues every frame. */
    val recordedBoxCount: Int get() = regions.size

    /** The distinct charts the recorded boxes touch, the full replay's per-frame uploads. */
    val touchedChartCount: Int get() = regions.map { it.texture }.distinct().size

    override fun bindTexture(
        target: GLenum,
        texture: GLTexture?,
    ) {
        checkOpen()
        bound = texture
        delegate.bindTexture(target, texture)
    }

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
    ) {
        checkOpen()
        val texture = bound
        if (srcData == null && texture != null) {
            shadows.getOrPut(texture) { TextureShadow.create(width, height, internalFormat, format, type) }
        }
        delegate.texImage2D(target, level, internalFormat, width, height, border, format, type, srcData)
    }

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
    ) {
        checkOpen()
        val texture = bound
        val shadow = if (texture == null) null else shadows[texture]
        if (texture != null && shadow != null) {
            shadow.copyIn(xOffset, yOffset, width, height, bytesPerPixel(format), srcData)
            regions += RecordedRegion.create(texture, xOffset, yOffset, width, height, format, type, shadow)
        }
        delegate.texSubImage2D(target, level, xOffset, yOffset, width, height, format, type, srcData)
    }

    /** A release path, so it still forwards after [close]: the engine deletes its own textures then. */
    override fun deleteTexture(texture: GLTexture) {
        shadows.remove(texture)?.close()
        if (bound == texture) bound = null
        releaseRegionsOf(texture)
        delegate.deleteTexture(texture)
    }

    /** Issues this frame's uploads under the policy: every box, or one whole chart per touched chart. */
    fun replayFrame() {
        checkOpen()
        when (policy) {
            UploadPolicy.REGION -> regions.forEach(::replayRegion)
            UploadPolicy.FULL -> {
                touchedCharts.clear()
                regions.mapTo(touchedCharts) { it.texture }
                touchedCharts.forEach(::replayWholeChart)
            }
        }
    }

    private fun replayRegion(region: RecordedRegion) {
        delegate.bindTexture(GL.TEXTURE_2D, region.texture)
        delegate.texSubImage2D(
            GL.TEXTURE_2D,
            0,
            region.x,
            region.y,
            region.width,
            region.height,
            region.format,
            region.type,
            region.bits.resource,
        )
    }

    private fun replayWholeChart(texture: GLTexture) {
        val shadow = shadows[texture] ?: return
        delegate.bindTexture(GL.TEXTURE_2D, texture)
        delegate.texImage2D(
            GL.TEXTURE_2D,
            0,
            shadow.internalFormat,
            shadow.width,
            shadow.height,
            0,
            shadow.format,
            shadow.type,
            shadow.buffer,
        )
    }

    /** Releases [texture]'s pre-packed boxes; a release path, like [deleteTexture]. */
    private fun releaseRegionsOf(texture: GLTexture) {
        val iterator = regions.iterator()
        val removed = mutableListOf<KGEResource>()
        while (iterator.hasNext()) {
            val region = iterator.next()
            if (region.texture == texture) {
                iterator.remove()
                removed += region
            }
        }
        removed.closeEach()
    }

    override fun close() {
        if (closed) return
        closed = true
        val toClose = mutableListOf<KGEResource>()
        toClose += shadows.values
        toClose += regions
        shadows.clear()
        regions.clear()
        touchedCharts.clear()
        bound = null
        toClose.closeEach()
    }

    private fun checkOpen() {
        check(!closed) { "the upload policy decorator is closed" }
    }
}

/** One recorded carrier upload: its box pre-packed once, so no measured frame pays a CPU copy. */
private class RecordedRegion(
    val texture: GLTexture,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val format: GLenum,
    val type: GLenum,
    val bits: ResourceWrapper<ByteBuffer>,
) : KGEResource {
    override fun close() = bits.close()

    companion object {
        /** Packs the box out of [shadow] in the client layout the replay re-issues. */
        fun create(
            texture: GLTexture,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            format: GLenum,
            type: GLenum,
            shadow: TextureShadow,
        ): RecordedRegion {
            val size = clientRowStride(width, shadow.bytesPerPixel) * height
            val storage = BufferService.allocate(size, "upload policy replay box")
            return storage.letClosingIfFailed { wrapper ->
                shadow.extract(x, y, width, height, wrapper.resource)
                RecordedRegion(texture, x, y, width, height, format, type, wrapper)
            }
        }
    }
}

/**
 * One texture's CPU shadow: the whole chart in the client layout a full upload
 * reads, seeded zeroed and maintained by every region update copied into it.
 */
private class TextureShadow private constructor(
    private val storage: ResourceWrapper<ByteBuffer>,
    val width: Int,
    val height: Int,
    val internalFormat: GLenum,
    val format: GLenum,
    val type: GLenum,
) : KGEResource {
    val bytesPerPixel: Int = bytesPerPixel(format)
    private val stride: Int = clientRowStride(width, bytesPerPixel)
    val buffer: ByteBuffer get() = storage.resource

    /** Copies the region's rows from its client layout into the shadow at the box offset. */
    fun copyIn(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        sourceBytesPerPixel: Int,
        source: ByteBuffer,
    ) {
        val sourceStride = clientRowStride(width, sourceBytesPerPixel)
        for (row in 0 until height) {
            for (byte in 0 until width * sourceBytesPerPixel) {
                buffer.put((y + row) * stride + x * bytesPerPixel + byte, source.get(row * sourceStride + byte))
            }
        }
    }

    /** Copies the region back into [destination]'s own client layout. */
    fun extract(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        destination: ByteBuffer,
    ) {
        val destinationStride = clientRowStride(width, bytesPerPixel)
        for (row in 0 until height) {
            for (byte in 0 until width * bytesPerPixel) {
                val source = (y + row) * stride + x * bytesPerPixel + byte
                destination.put(row * destinationStride + byte, buffer.get(source))
            }
        }
    }

    override fun close() = storage.close()

    companion object {
        /** Allocates the zeroed shadow; a failure after allocation closes it. */
        fun create(
            width: Int,
            height: Int,
            internalFormat: GLenum,
            format: GLenum,
            type: GLenum,
        ): TextureShadow {
            val bytesPerPixel = bytesPerPixel(format)
            val size = clientRowStride(width, bytesPerPixel) * height
            val storage = BufferService.allocate(size, "upload policy texture shadow (${width}x$height)")
            return storage.letClosingIfFailed { wrapper ->
                val buffer = wrapper.resource
                for (offset in 0 until size) {
                    buffer.put(offset, 0)
                }
                TextureShadow(wrapper, width, height, internalFormat, format, type)
            }
        }
    }
}

/** The default `GL_UNPACK_ALIGNMENT`: every client row starts 4-byte aligned. */
private const val CLIENT_ALIGNMENT = 4

private fun clientRowStride(
    width: Int,
    bytesPerPixel: Int,
): Int = (width * bytesPerPixel + CLIENT_ALIGNMENT - 1) and (CLIENT_ALIGNMENT - 1).inv()

private fun bytesPerPixel(format: GLenum): Int = if (format == GL.RED) 1 else Int.SIZE_BYTES

/** Closes every resource, rethrowing the first failure with the later ones suppressed. */
private fun Iterable<KGEResource>.closeEach() {
    var failure: Throwable? = null
    for (resource in this) {
        try {
            resource.close()
        } catch (throwable: Throwable) {
            val first = failure
            if (first == null) failure = throwable else first.addSuppressed(throwable)
        }
    }
    failure?.let { throw it }
}
