package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.ResourceWrapper
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import org.lwjgl.util.harfbuzz.HarfBuzz
import org.lwjgl.util.harfbuzz.hb_font_extents_t
import kotlin.math.abs

/**
 * JVM face over LWJGL HarfBuzz and FreeType: both reference the engine buffer
 * read-only, so the face owns the payload and releases it after the handles.
 */
internal actual class NativeFace(
    private val payload: ResourceWrapper<ByteBuffer>,
    private val blob: Long,
    private val face: Long,
    private val font: Long,
    private val ftFace: FT_Face,
) {
    private var ftSizePx: Int = 0

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

    actual fun rasterize(
        glyphId: Int,
        sizePx: Int,
    ): GlyphCoverage {
        if (ftSizePx != sizePx) {
            val error = FreeType.FT_Set_Pixel_Sizes(ftFace, 0, sizePx)
            check(error == FreeType.FT_Err_Ok) { "FreeType could not set the pixel size $sizePx: $error" }
            ftSizePx = sizePx
        }
        var error = FreeType.FT_Load_Glyph(ftFace, glyphId, FreeType.FT_LOAD_DEFAULT)
        check(error == FreeType.FT_Err_Ok) { "FreeType could not load glyph $glyphId: $error" }
        val slot = checkNotNull(ftFace.glyph()) { "FreeType returned no glyph slot" }
        error = FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_NORMAL)
        check(error == FreeType.FT_Err_Ok) { "FreeType could not render glyph $glyphId: $error" }

        val bitmap = slot.bitmap()
        val width = bitmap.width()
        val height = bitmap.rows()
        val bearing = Int2D(slot.bitmap_left(), -slot.bitmap_top())
        if (width == 0 || height == 0) return GlyphCoverage(0, 0, bearing, ByteArray(0))
        require(bitmap.pixel_mode().toInt() == FreeType.FT_PIXEL_MODE_GRAY) {
            "the glyph bitmap is not grayscale: pixel mode ${bitmap.pixel_mode().toInt()}"
        }

        val pitch = bitmap.pitch()
        val stride = abs(pitch)
        val source = checkNotNull(bitmap.buffer(stride * height)) { "FreeType returned no bitmap buffer" }
        val numGrays = bitmap.num_grays().toInt()
        val coverage = ByteArray(width * height)
        for (row in 0 until height) {
            // An up-flow bitmap stores its bottom row first: walk the rows backwards.
            val from = (if (pitch < 0) height - 1 - row else row) * stride
            for (column in 0 until width) {
                val alpha = source.get(from + column).toInt() and 0xFF
                coverage[row * width + column] =
                    (if (numGrays == 256) alpha else alpha * 256 / numGrays).toByte()
            }
        }
        return GlyphCoverage(width, height, bearing, coverage)
    }

    /** Destroys the FreeType face, the HarfBuzz handles, then the payload they stood on. */
    internal fun release() {
        FreeType.FT_Done_Face(ftFace)
        HarfBuzz.hb_font_destroy(font)
        HarfBuzz.hb_face_destroy(face)
        HarfBuzz.hb_blob_destroy(blob)
        payload.close()
    }
}

internal actual suspend fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace {
    val buffer = bytes.resource
    val blob = HarfBuzz.hb_blob_create(buffer, HarfBuzz.HB_MEMORY_MODE_READONLY, MemoryUtil.NULL, null)
    check(blob != MemoryUtil.NULL) { "HarfBuzz could not map the font payload" }

    var face = MemoryUtil.NULL
    var font = MemoryUtil.NULL
    var ftFace: FT_Face? = null
    try {
        face = HarfBuzz.hb_face_create(blob, 0)
        if (face == MemoryUtil.NULL) {
            throw IllegalArgumentException("the face can not be opened: the payload is not a usable font")
        }
        if (HarfBuzz.hb_face_get_glyph_count(face) == 0) {
            throw IllegalArgumentException("the face has no glyphs: the payload is not a usable font")
        }
        font = HarfBuzz.hb_font_create(face)
        check(font != MemoryUtil.NULL) { "HarfBuzz could not create a font from the face" }
        ftFace = openFreeTypeFace(buffer)
        return NativeFace(bytes, blob, face, font, ftFace)
    } catch (e: Throwable) {
        if (ftFace != null) FreeType.FT_Done_Face(ftFace)
        if (font != MemoryUtil.NULL) HarfBuzz.hb_font_destroy(font)
        if (face != MemoryUtil.NULL) HarfBuzz.hb_face_destroy(face)
        HarfBuzz.hb_blob_destroy(blob)
        throw e
    }
}

internal actual fun closeNativeFace(face: NativeFace) {
    face.release()
}

/** Opens the payload as a FreeType face; FreeType does not copy the bytes. */
private fun openFreeTypeFace(buffer: ByteBuffer): FT_Face {
    MemoryStack.stackPush().use { stack ->
        val handle = stack.mallocPointer(1)
        val error = FreeType.FT_New_Memory_Face(freeTypeLibrary, buffer, 0L, handle)
        if (error != FreeType.FT_Err_Ok) {
            throw IllegalArgumentException("the face can not be opened: the payload is not a usable font ($error)")
        }
        return FT_Face.create(handle[0])
    }
}

/** Process-scoped: the per-font face is the resource, the library never is. */
private val freeTypeLibrary: Long by lazy {
    MemoryStack.stackPush().use { stack ->
        val library = stack.mallocPointer(1)
        val error = FreeType.FT_Init_FreeType(library)
        check(error == FreeType.FT_Err_Ok) { "FreeType could not be initialized: $error" }
        library[0]
    }
}

/** Positions are 26.6 fixed point: `sizePx` scales by [FIXED_POINT_SCALE]. */
private const val FIXED_POINT_SCALE: Int = 64
