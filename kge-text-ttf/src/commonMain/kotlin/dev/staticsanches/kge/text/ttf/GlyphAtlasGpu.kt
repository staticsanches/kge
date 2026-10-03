@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.putByte
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The GPU shadow of one size's [GlyphAtlas]: one texture per chart, each of its
 * placed boxes uploaded once; close releases every texture and the scratch buffer.
 */
internal class GlyphAtlasGpu(
    private val atlas: GlyphAtlas,
) : KGEResource {
    private val decalsByChartIndex = mutableMapOf<Int, Decal>()
    private val uploadedPlacements = mutableSetOf<AtlasGlyph.Placed>()
    private var scratch: ResourceWrapper<ByteBuffer>? = null
    private var closed = false

    /** The chart's decal, created on first use; the box is uploaded when first seen. */
    internal fun decalFor(placed: AtlasGlyph.Placed): Decal {
        check(!closed) { "the glyph coverage carrier has been released and can not be used" }

        val decal = decalsByChartIndex.getOrPut(placed.chartIndex) { createDecal(placed.chartIndex) }
        if (placed !in uploadedPlacements) {
            upload(decal, placed)
            uploadedPlacements += placed
        }
        return decal
    }

    private fun createDecal(chartIndex: Int): Decal {
        val chart = atlas.charts[chartIndex]
        val handle = GL.createTexture()
        val wrapper =
            ResourceWrapper(
                "glyph coverage texture (${chart.width}x${chart.height})",
                handle,
                KGECleanAction { GL.deleteTexture(handle) },
            )
        return wrapper.letClosingIfFailed { storage ->
            GL.bindTexture(GL.TEXTURE_2D, storage.resource)
            GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)
            GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)
            GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
            GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
            if (coverageTextureIsSingleChannel) {
                GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_R, GL.ONE)
                GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_G, GL.ONE)
                GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_B, GL.ONE)
                GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_A, GL.RED)
            }
            GL.texImage2D(
                GL.TEXTURE_2D,
                0,
                coverageInternalFormat,
                chart.width,
                chart.height,
                0,
                coverageUploadFormat,
                GL.UNSIGNED_BYTE,
                null,
            )
            Decal(Texture(storage), chart)
        }
    }

    private fun upload(
        decal: Decal,
        placed: AtlasGlyph.Placed,
    ) {
        val buffer = scratchFor(placed)
        packBox(atlas.charts[placed.chartIndex], placed, buffer)
        decal.texture.apply()
        GL.texSubImage2D(
            GL.TEXTURE_2D,
            0,
            placed.source.x,
            placed.source.y,
            placed.size.x,
            placed.size.y,
            coverageUploadFormat,
            GL.UNSIGNED_BYTE,
            buffer,
        )
    }

    private fun scratchFor(placed: AtlasGlyph.Placed): ByteBuffer {
        val needed = clientRowStride(placed.size.x) * placed.size.y
        val existing = scratch
        if (existing != null && existing.resource.capacity() >= needed) return existing.resource

        val grown = BufferService.allocate(needed, "glyph coverage scratch")
        scratch = grown
        existing?.close()
        return grown.resource
    }

    private fun packBox(
        chart: Sprite,
        placed: AtlasGlyph.Placed,
        buffer: ByteBuffer,
    ) {
        val rowStride = clientRowStride(placed.size.x)
        for (row in 0 until placed.size.y) {
            for (column in 0 until placed.size.x) {
                val pixel = chart.get(placed.source.x + column, placed.source.y + row)
                val offset = row * rowStride + column * coverageBytesPerPixel
                if (coverageTextureIsSingleChannel) {
                    buffer.putByte(offset, pixel.a)
                } else {
                    buffer.putInt(offset, pixel.nativeRGBA)
                }
            }
        }
    }

    /** The row stride the driver assumes with the default 4-byte unpack alignment. */
    private fun clientRowStride(width: Int): Int =
        (width * coverageBytesPerPixel + CLIENT_ALIGNMENT - 1) and (CLIENT_ALIGNMENT - 1).inv()

    override fun close() {
        if (closed) return
        closed = true
        val toClose = mutableListOf<KGEResource>()
        toClose += decalsByChartIndex.values
        scratch?.let { toClose += it }
        decalsByChartIndex.clear()
        uploadedPlacements.clear()
        scratch = null
        toClose.closeAll()
    }
}

/** True where the coverage uploads as one channel and is sampled through a texture swizzle. */
internal expect val coverageTextureIsSingleChannel: Boolean

private val coverageInternalFormat: GLenum
    get() = if (coverageTextureIsSingleChannel) GL.R8 else GL.RGBA

private val coverageUploadFormat: GLenum
    get() = if (coverageTextureIsSingleChannel) GL.RED else GL.RGBA

private val coverageBytesPerPixel: Int
    get() = if (coverageTextureIsSingleChannel) 1 else Int.SIZE_BYTES

/** The default `GL_UNPACK_ALIGNMENT`: every client row starts 4-byte aligned. */
private const val CLIENT_ALIGNMENT = 4
