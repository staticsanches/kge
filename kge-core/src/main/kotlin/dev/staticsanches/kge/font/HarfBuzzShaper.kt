package dev.staticsanches.kge.font

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.font.TextShaper.ShapedGlyph
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.PointerResource
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.invokeForAll
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.harfbuzz.HarfBuzz
import java.nio.IntBuffer
import java.util.concurrent.ConcurrentHashMap

internal class HarfBuzzShaper private constructor(
    val face: TypeFace,
    val size: Int,
    val height: FPU,
    val ascender: FPU,
    private val buffer: PointerResource,
    private val font: PointerResource,
) : KGEInternalResource {
    fun shape(
        codePoints: IntBuffer,
        language: String,
        script: ScriptTag,
        penPositionStart: FPU2D,
        color: Pixel,
        shapedGlyphs: MutableList<ShapedGlyph>,
    ): FPU2D =
        face.internalFace.withSize(size) {
            val buffer = buffer.handle

            // 1. Reset the buffer
            HarfBuzz.hb_buffer_reset(buffer)

            // 2. Configure buffer
            HarfBuzz.hb_buffer_set_direction(buffer, HarfBuzz.HB_DIRECTION_LTR)
            HarfBuzz.hb_buffer_set_script(buffer, script.toScriptCode())
            HarfBuzz.hb_buffer_set_language(buffer, language.toLanguageHandle())

            // 3. Append text
            HarfBuzz.hb_buffer_add_codepoints(buffer, codePoints, codePoints.position(), codePoints.remaining())

            // 4. Shape text
            HarfBuzz.hb_shape(font.handle, buffer, null)

            // 5. Extract glyph data
            val infos =
                checkNotNull(HarfBuzz.hb_buffer_get_glyph_infos(buffer)?.iterator()) {
                    error("Unable to retrieve glyph infos")
                }
            val positions =
                checkNotNull(HarfBuzz.hb_buffer_get_glyph_positions(buffer)?.iterator()) {
                    error("Unable to retrieve glyph positions")
                }

            var penPosition = penPositionStart
            while (infos.hasNext() && positions.hasNext()) {
                val info = infos.next()
                val position = positions.next()

                val offset = FPU(position.x_offset()) by FPU(position.y_offset())
                val advance = FPU(position.x_advance()) by FPU(position.y_advance())

                shapedGlyphs +=
                    ShapedGlyph(
                        face = face,
                        size = size,
                        glyphIndex = info.codepoint(),
                        penPosition = penPosition + offset,
                        color = color,
                    )

                penPosition += advance
            }

            check(!infos.hasNext() && !positions.hasNext()) { error("Incompatible glyph infos") }

            return penPosition
        }

    private fun error(message: String) = "[${face.name} - ${size}px] $message"

    @KGESensitiveAPI
    override fun close() = invokeForAll(buffer, font) { it.close() }

    override fun toString(): String = "Shaper for ${face.name} - ${size}px"

    companion object {
        operator fun invoke(
            face: TypeFace,
            size: Int,
        ): HarfBuzzShaper =
            face.internalFace.withSize(size) { ftFace ->
                PointerResource(
                    "hb_buffer",
                    "${face.name} - ${size}px",
                    ::createBuffer,
                    ::BufferDestroyAction,
                ).closeIfFailed { buffer ->
                    PointerResource(
                        "hb_font",
                        "${face.name} - ${size}px",
                        { createFont(ftFace) },
                        ::FontDestroyAction,
                    ).closeIfFailed { font ->
                        val metrics =
                            checkNotNull(
                                ftFace.size()?.metrics(),
                            ) { "[${face.name} - ${size}px] Unable to retrieve size metrics" }
                        HarfBuzzShaper(face, size, FPU(metrics.height()), FPU(metrics.ascender()), buffer, font)
                    }
                }
            }

        private fun createBuffer(): Long {
            val handle = HarfBuzz.hb_buffer_create()
            check(HarfBuzz.hb_buffer_allocation_successful(handle)) { "Error creating hb_buffer" }
            return handle
        }

        private fun createFont(face: FT_Face): Long {
            val handle = HarfBuzz.nhb_ft_font_create(face.address(), MemoryUtil.NULL)
            HarfBuzz.hb_font_make_immutable(handle)
            return handle
        }

        private val scriptCache = ConcurrentHashMap<ScriptTag, Int>()
        private val languageCache = ConcurrentHashMap<String, Long>()

        private fun ScriptTag.toScriptCode(): Int =
            scriptCache.computeIfAbsent(this) { HarfBuzz.hb_script_from_string(it.name) }

        private fun String.toLanguageHandle(): Long =
            languageCache.computeIfAbsent(this, HarfBuzz::hb_language_from_string)
    }
}

@JvmInline
private value class BufferDestroyAction(
    val handle: Long,
) : KGECleanAction {
    override fun invoke() = HarfBuzz.hb_buffer_destroy(handle)
}

@JvmInline
private value class FontDestroyAction(
    val handle: Long,
) : KGECleanAction {
    override fun invoke() = HarfBuzz.hb_font_destroy(handle)
}
