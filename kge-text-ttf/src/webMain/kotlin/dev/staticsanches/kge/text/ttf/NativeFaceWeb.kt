package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.ResourceWrapper
import org.khronos.webgl.DataView
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toList
import kotlin.math.abs

/**
 * Web face: `harfbuzzjs` and the FreeType module each copy the payload into wasm
 * memory, so the engine buffer is staging and is released once both faces exist.
 * Release drops the HarfBuzz reference and destroys the FreeType face.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal actual class NativeFace internal constructor(
    private var handle: HarfBuzzFont?,
    private val freeTypeFace: Face,
) {
    private var ftSizePx: Int = 0

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

    actual fun rasterize(
        glyphId: Int,
        sizePx: Int,
    ): GlyphCoverage {
        checkNotNull(handle) { "the face has been released" }
        if (ftSizePx != sizePx) {
            freeTypeFace.setPixelSize(sizePx)
            ftSizePx = sizePx
        }
        val glyph = freeTypeFace.loadGlyph(loadGlyphOptions(glyphId))
        val width = glyph.width
        val height = glyph.rows
        val bearing = Int2D(glyph.bitmapLeft, -glyph.bitmapTop)
        if (width == 0 || height == 0) return GlyphCoverage(0, 0, bearing, ByteArray(0))
        require(glyph.pixelMode == FreeTypeConstants.PIXEL_MODE_GRAY) {
            "the glyph bitmap is not grayscale: pixel mode ${glyph.pixelMode}"
        }

        val pitch = glyph.pitch
        val stride = abs(pitch)
        val numGrays = glyph.numGrays
        val coverage = ByteArray(width * height)
        for (row in 0 until height) {
            // An up-flow bitmap stores its bottom row first: walk the rows backwards.
            val from = (if (pitch < 0) height - 1 - row else row) * stride
            for (column in 0 until width) {
                val alpha = glyph.buffer[from + column].toInt() and 0xFF
                coverage[row * width + column] =
                    (if (numGrays == 256) alpha else alpha * 256 / numGrays).toByte()
            }
        }
        return GlyphCoverage(width, height, bearing, coverage)
    }

    /** Destroys the FreeType face's wasm copy and drops the HarfBuzz reference. */
    internal fun release() {
        freeTypeFace.destroy()
        handle = null
    }
}

internal actual suspend fun createNativeFace(bytes: ResourceWrapper<ByteBuffer>): NativeFace {
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
    // The shared module copies the bytes into its own heap, as HarfBuzz's blob does.
    val freeTypeFace = freeTypeModule().newFace(data)
    val native: NativeFace
    try {
        native = NativeFace(HarfBuzzFont(face), freeTypeFace)
    } catch (failure: Throwable) {
        // Only Face.destroy() frees the FreeType heap copy; no finalizer will.
        freeTypeFace.destroy()
        throw failure
    }
    // The bytes now live in wasm memory: the staging buffer is done.
    bytes.close()
    return native
}

internal actual fun closeNativeFace(face: NativeFace) {
    face.release()
}

/**
 * A plain JS object literal: the `js-plain-objects` compiler plugin that builds
 * `@JsPlainObject` options is JS-only, so it can not serve the wasmJs target.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun loadGlyphOptions(index: Int): LoadGlyphOptions = js("({ index: index })")

/** Positions are 26.6 fixed point: `sizePx` scales by [FIXED_POINT_SCALE]. */
private const val FIXED_POINT_SCALE: Int = 64
