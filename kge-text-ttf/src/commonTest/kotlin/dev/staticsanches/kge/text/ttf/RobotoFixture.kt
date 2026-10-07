package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.font.roboto.RobotoMono
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.decal.Decal
import kotlin.io.encoding.Base64

/** The bundled Roboto fixture, decoded from the chunked base64 the data module ships. */
fun robotoFontBytes(): ByteArray = Base64.decode(Roboto.romanFont.joinToString(""))

/** The bundled Roboto italic fixture, decoded from its chunked base64. */
fun robotoItalicBytes(): ByteArray = Base64.decode(Roboto.italicFont.joinToString(""))

/** The bundled Roboto Mono roman fixture, decoded from its chunked base64. */
fun robotoMonoBytes(): ByteArray = Base64.decode(RobotoMono.romanFont.joinToString(""))

/** The bundled Roboto Mono italic fixture, decoded from its chunked base64. */
fun robotoMonoItalicBytes(): ByteArray = Base64.decode(RobotoMono.italicFont.joinToString(""))

/** One shared payload behind the face; the face owns none of it, so this closes both. */
internal suspend fun <T> withRobotoFace(
    bytes: ByteArray = robotoFontBytes(),
    block: (NativeFace) -> T,
): T {
    val payload = TtfPayload(bytes)
    try {
        val face = createNativeFace(payload, AxisCoordinates.Empty)
        try {
            return block(face)
        } finally {
            closeNativeFace(face)
        }
    } finally {
        payload.close()
    }
}

/** Runs [block] over [bytes]' [sizePx] anchor; the face, atlas and GPU carrier close with it. */
internal suspend fun <T> withRobotoAtlas(
    sizePx: Int = 16,
    bytes: ByteArray = robotoFontBytes(),
    block: (RobotoAtlas) -> T,
): T =
    withRobotoFace(bytes) { face ->
        val atlas = GlyphAtlas(sizePx) { glyphId -> face.rasterize(glyphId, sizePx) }
        try {
            val gpu = GlyphAtlasGpu(atlas)
            try {
                block(RobotoAtlas(face, sizePx, atlas, gpu))
            } finally {
                gpu.close()
            }
        } finally {
            atlas.close()
        }
    }

/** One size's Roboto anchor: the face, its glyph atlas and the GPU carrier over it. */
internal class RobotoAtlas(
    private val face: NativeFace,
    private val sizePx: Int,
    val atlas: GlyphAtlas,
    val gpu: GlyphAtlasGpu,
) {
    /** The shaped glyphs of [text] at this size. */
    fun glyphs(text: String): List<ShapedGlyph> = face.shape(text.toCodePoints(), sizePx)

    /** The atlas box of [text]'s shaped glyph at [index]. */
    fun placed(
        text: String,
        index: Int = 0,
    ): AtlasGlyph.Placed = atlas.glyph(glyphs(text)[index].glyphId) as AtlasGlyph.Placed

    /** The GPU decal of [text]'s shaped glyph at [index], created and uploaded on first use. */
    fun decal(
        text: String,
        index: Int = 0,
    ): Decal = gpu.decalFor(placed(text, index))

    /** The chart the [placed] box was packed onto. */
    fun chart(placed: AtlasGlyph.Placed): Sprite = atlas.charts[placed.chartIndex]
}
