package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toList
import kotlin.math.abs

/**
 * Web face: `harfbuzzjs` and the FreeType module each copy the shared payload
 * into wasm memory. Release drops the HarfBuzz reference and destroys the FreeType face.
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

internal actual suspend fun createNativeFace(
    payload: TtfPayload,
    coordinates: AxisCoordinates,
): NativeFace {
    val data = payload.buffer.resource.nativeBytes
    val face = HarfBuzzFace(HarfBuzzBlob(data), 0)
    if (face.referenceTable("cmap") == null) {
        throw IllegalArgumentException("the face has no cmap table: the payload is not a usable font")
    }
    // The shared module copies the bytes into its own heap, as HarfBuzz's blob does.
    val freeType = freeTypeModule()
    val freeTypeFace = freeType.newFace(data)
    try {
        val font = HarfBuzzFont(face)
        setDesignCoordinates(font, freeType.module, freeTypeFace.ptr, coordinates)
        return NativeFace(font, freeTypeFace)
    } catch (failure: Throwable) {
        // Only Face.destroy() frees the FreeType heap copy; no finalizer will.
        freeTypeFace.destroy()
        throw failure
    }
}

internal actual fun closeNativeFace(face: NativeFace) {
    face.release()
}

/**
 * Pushes the whole coordinate set into both engines: neither reads the other's
 * store, and HarfBuzz resets every axis a partial set omits.
 */
private fun setDesignCoordinates(
    font: HarfBuzzFont,
    module: FreeTypeRawModule,
    facePointer: Int,
    coordinates: AxisCoordinates,
) {
    if (coordinates.isEmpty) return
    val variations =
        coordinates.tags.indices.map { index ->
            HarfBuzzVariation(axisTag(coordinates.tags[index]), coordinates.values[index] / DESIGN_COORDINATE_SCALE)
        }
    font.setVariations(variations.toJsArray())

    val size = coordinates.values.size
    val coords = module.malloc(size * Int.SIZE_BYTES)
    check(coords != 0) { "FreeType could not allocate the coordinate array" }
    try {
        // The 16.16 values are wasm32 ints, and the heap view moves whenever the heap grows.
        val heap = module.heap32
        for (index in 0 until size) heap[(coords ushr 2) + index] = coordinates.values[index]
        val error = module.setVarDesignCoordinates(facePointer, size, coords)
        check(error == FT_ERR_OK) { "FreeType could not apply the design coordinates: $error" }
    } finally {
        module.free(coords)
    }
}

/** The tag's four characters, most significant byte first. */
private fun axisTag(tag: Int): String =
    buildString(4) {
        for (shift in 24 downTo 0 step 8) append(((tag ushr shift) and 0xFF).toChar())
    }

/**
 * A plain JS object literal: the `js-plain-objects` compiler plugin that builds
 * `@JsPlainObject` options is JS-only, so it can not serve the wasmJs target.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun loadGlyphOptions(index: Int): LoadGlyphOptions = js("({ index: index })")

/** Positions are 26.6 fixed point: `sizePx` scales by [FIXED_POINT_SCALE]. */
private const val FIXED_POINT_SCALE: Int = 64

/** Design coordinates are 16.16 fixed point: HarfBuzz takes their plain value. */
private const val DESIGN_COORDINATE_SCALE: Float = 65536f

/** `FT_Err_Ok`; the package's `FT` object does not carry it. */
private const val FT_ERR_OK: Int = 0
