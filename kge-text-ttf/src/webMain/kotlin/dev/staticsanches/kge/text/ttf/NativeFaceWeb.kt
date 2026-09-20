package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.resource.ResourceWrapper
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toList

/**
 * Web face: `harfbuzzjs` copies the payload into wasm memory, so the engine
 * buffer is staging and is released once the face exists. Release on web can
 * only drop the references: the module exposes no deterministic free.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal actual class NativeFace internal constructor(
    private var handle: HarfBuzzFont?,
) {
    actual fun shape(
        codePoints: IntArray,
        sizePx: Int,
    ): List<ShapedGlyph> {
        val font = checkNotNull(handle) { "the face has been released" }
        font.setScale(sizePx * FIXED_POINT_SCALE, sizePx * FIXED_POINT_SCALE)

        val buffer = HarfBuzzBuffer()
        buffer.setDirection(HarfBuzzDirection.LTR)
        buffer.setScript("Latn")
        if (codePoints.isNotEmpty()) {
            buffer.addCodePoints(codePoints.map { it.toJsNumber() }.toJsArray(), 0, codePoints.size)
        }
        harfBuzzShape(font, buffer)

        val infos = buffer.getGlyphInfos().toList()
        val positions = buffer.getGlyphPositions().toList()
        return infos.indices.map { index ->
            val info = infos[index]
            val position = positions[index]
            ShapedGlyph(
                glyphId = info.codepoint,
                cluster = info.cluster,
                offset =
                    Float2D(
                        position.xOffset / FIXED_POINT_SCALE.toFloat(),
                        position.yOffset / FIXED_POINT_SCALE.toFloat(),
                    ),
                advance =
                    Float2D(
                        position.xAdvance / FIXED_POINT_SCALE.toFloat(),
                        position.yAdvance / FIXED_POINT_SCALE.toFloat(),
                    ),
            )
        }
    }

    actual fun metrics(sizePx: Int): TextMetrics {
        val font = checkNotNull(handle) { "the face has been released" }
        font.setScale(sizePx * FIXED_POINT_SCALE, sizePx * FIXED_POINT_SCALE)
        val extents = font.hExtents()
        return TextMetrics(
            ascender = extents.ascender / FIXED_POINT_SCALE.toFloat(),
            descender = extents.descender / FIXED_POINT_SCALE.toFloat(),
            lineGap = extents.lineGap / FIXED_POINT_SCALE.toFloat(),
        )
    }

    /** Drops the wasm font reference; the payload was released when the face was opened. */
    internal fun release() {
        handle = null
    }
}

internal actual fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace {
    val buffer = bytes.resource
    val data = Uint8Array(buffer.capacity())
    val view = DataView(data.buffer)
    for (index in 0 until buffer.capacity()) {
        view.setUint8(index, buffer.get(index))
    }

    val face = HarfBuzzFace(HarfBuzzBlob(data), 0)
    if (face.referenceTable("cmap") == null) {
        throw IllegalArgumentException("the face has no cmap table: the payload is not a usable font")
    }
    val native = NativeFace(HarfBuzzFont(face))
    // The bytes now live in wasm memory: the staging buffer is done.
    bytes.close()
    return native
}

internal actual fun closeNativeFace(face: NativeFace) {
    face.release()
}

/** Positions are 26.6 fixed point: `sizePx` scales by [FIXED_POINT_SCALE]. */
private const val FIXED_POINT_SCALE: Int = 64
