package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper

/**
 * An open native face over a payload it does not own: closing releases native
 * handles only, so the payload must outlive the face. Built by the seam's factory.
 */
internal expect class NativeFace {
    fun shape(
        codePoints: IntArray,
        sizePx: Int,
    ): List<ShapedGlyph>

    fun metrics(sizePx: Int): TextMetrics

    /** Renders [glyphId] at [sizePx]; a glyph with no ink yields no coverage. */
    fun rasterize(
        glyphId: Int,
        sizePx: Int,
    ): GlyphCoverage
}

/** One rendered glyph: row-major, top-down, one coverage byte per pixel. */
internal class GlyphCoverage(
    val width: Int,
    val height: Int,
    /** Pen to bitmap top-left, in whole pixels, y down. */
    val bearing: Int2D,
    /** `width * height` bytes; empty when the glyph has no ink. */
    val coverage: ByteArray,
)

/** Applied axis coordinates: tags and raw 16.16 values in `fvar` order. */
internal class AxisCoordinates(
    val tags: IntArray,
    val values: IntArray,
) {
    val isEmpty: Boolean get() = tags.isEmpty()

    companion object {
        /** The default instance: no engine call at all. */
        val Empty: AxisCoordinates = AxisCoordinates(IntArray(0), IntArray(0))
    }
}

/**
 * Opens [payload] as its first face and applies [coordinates] to both engines
 * when they are non-empty; a payload that is not a usable font is rejected.
 */
internal expect suspend fun createNativeFace(
    payload: TtfPayload,
    coordinates: AxisCoordinates,
): NativeFace

/** Releases the native handles, never the shared payload; the wrapper runs it at most once. */
internal expect fun closeNativeFace(face: NativeFace)

/** Creates the resource that owns [face]; a failed hand-off closes [face] instead. */
@OptIn(KGESensitiveAPI::class)
internal fun wrapNativeFace(face: NativeFace): ResourceWrapper<NativeFace> =
    try {
        ResourceWrapper("font face", face, KGECleanAction { closeNativeFace(face) })
    } catch (failure: Throwable) {
        try {
            closeNativeFace(face)
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw failure
    }

/** The text's Unicode code points, pairing a surrogate pair into one. */
internal fun String.toCodePoints(): IntArray {
    val codePoints = ArrayList<Int>(length)
    var index = 0
    while (index < length) {
        val high = this[index]
        if (high.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
            codePoints +=
                SUPPLEMENTARY_CODE_POINT_OFFSET +
                ((high.code - Char.MIN_HIGH_SURROGATE.code) shl 10) +
                (this[index + 1].code - Char.MIN_LOW_SURROGATE.code)
            index += 2
        } else {
            codePoints += high.code
            index++
        }
    }
    return codePoints.toIntArray()
}

private const val SUPPLEMENTARY_CODE_POINT_OFFSET = 0x10000
