package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.resource.ResourceWrapper
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.harfbuzz.HarfBuzz
import org.lwjgl.util.harfbuzz.hb_font_extents_t

/**
 * JVM face over LWJGL HarfBuzz: the blob references the engine buffer
 * read-only, so the face owns the payload and releases it after the handles.
 */
internal actual class NativeFace(
    private val payload: ResourceWrapper<ByteBuffer>,
    private val blob: Long,
    private val face: Long,
    private val font: Long,
) {
    actual fun shape(
        codePoints: IntArray,
        sizePx: Int,
    ): List<ShapedGlyph> {
        HarfBuzz.hb_font_set_scale(font, sizePx * FIXED_POINT_SCALE, sizePx * FIXED_POINT_SCALE)
        val buffer = HarfBuzz.hb_buffer_create()
        check(buffer != MemoryUtil.NULL) { "HarfBuzz could not create a buffer" }
        try {
            HarfBuzz.hb_buffer_set_direction(buffer, HarfBuzz.HB_DIRECTION_LTR)
            HarfBuzz.hb_buffer_set_script(buffer, HarfBuzz.HB_SCRIPT_LATIN)
            if (codePoints.isNotEmpty()) {
                val text = MemoryUtil.memAllocInt(codePoints.size)
                try {
                    text.put(codePoints)
                    text.flip()
                    HarfBuzz.hb_buffer_add_utf32(buffer, text, 0, text.remaining())
                } finally {
                    MemoryUtil.memFree(text)
                }
            }
            HarfBuzz.hb_shape(font, buffer, null)

            val length = HarfBuzz.hb_buffer_get_length(buffer)
            if (length == 0) return emptyList()
            val infos = HarfBuzz.hb_buffer_get_glyph_infos(buffer) ?: error("HarfBuzz returned no glyph infos")
            val positions =
                HarfBuzz.hb_buffer_get_glyph_positions(buffer)
                    ?: error("HarfBuzz returned no glyph positions")
            val glyphs = ArrayList<ShapedGlyph>(length)
            for (index in 0 until length) {
                val info = infos[index]
                val position = positions[index]
                glyphs +=
                    ShapedGlyph(
                        glyphId = info.codepoint(),
                        cluster = info.cluster(),
                        offset =
                            Float2D(
                                position.x_offset() / FIXED_POINT_SCALE.toFloat(),
                                position.y_offset() / FIXED_POINT_SCALE.toFloat(),
                            ),
                        advance =
                            Float2D(
                                position.x_advance() / FIXED_POINT_SCALE.toFloat(),
                                position.y_advance() / FIXED_POINT_SCALE.toFloat(),
                            ),
                    )
            }
            return glyphs
        } finally {
            HarfBuzz.hb_buffer_destroy(buffer)
        }
    }

    actual fun metrics(sizePx: Int): TextMetrics {
        HarfBuzz.hb_font_set_scale(font, sizePx * FIXED_POINT_SCALE, sizePx * FIXED_POINT_SCALE)
        MemoryStack.stackPush().use { stack ->
            val extents = hb_font_extents_t.calloc(stack)
            HarfBuzz.hb_font_get_h_extents(font, extents)
            return TextMetrics(
                ascender = extents.ascender() / FIXED_POINT_SCALE.toFloat(),
                descender = extents.descender() / FIXED_POINT_SCALE.toFloat(),
                lineGap = extents.line_gap() / FIXED_POINT_SCALE.toFloat(),
            )
        }
    }

    /** Destroys font, face and blob, then releases the payload the blob referenced. */
    internal fun release() {
        HarfBuzz.hb_font_destroy(font)
        HarfBuzz.hb_face_destroy(face)
        HarfBuzz.hb_blob_destroy(blob)
        payload.close()
    }
}

internal actual fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace {
    val buffer = bytes.resource
    val blob = HarfBuzz.hb_blob_create(buffer, HarfBuzz.HB_MEMORY_MODE_READONLY, MemoryUtil.NULL, null)
    check(blob != MemoryUtil.NULL) { "HarfBuzz could not map the font payload" }

    var face = MemoryUtil.NULL
    try {
        face = HarfBuzz.hb_face_create(blob, 0)
        if (face == MemoryUtil.NULL) {
            throw IllegalArgumentException("the face can not be opened: the payload is not a usable font")
        }
        if (HarfBuzz.hb_face_get_glyph_count(face) == 0) {
            throw IllegalArgumentException("the face has no glyphs: the payload is not a usable font")
        }
        val font = HarfBuzz.hb_font_create(face)
        check(font != MemoryUtil.NULL) { "HarfBuzz could not create a font from the face" }
        return NativeFace(bytes, blob, face, font)
    } catch (e: Throwable) {
        if (face != MemoryUtil.NULL) HarfBuzz.hb_face_destroy(face)
        HarfBuzz.hb_blob_destroy(blob)
        throw e
    }
}

internal actual fun closeNativeFace(face: NativeFace) {
    face.release()
}

/** Positions are 26.6 fixed point: `sizePx` scales by [FIXED_POINT_SCALE]. */
private const val FIXED_POINT_SCALE: Int = 64
