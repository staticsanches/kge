@file:JsModule("harfbuzzjs")
@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalJsCollectionsApi::class)

package dev.staticsanches.kge.text.ttf

import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalJsCollectionsApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsModule
import kotlin.js.JsName
import kotlin.js.JsNumber

/** The `harfbuzzjs` blob; its constructor copies the bytes into wasm memory. */
@JsName("Blob")
internal external class HarfBuzzBlob(
    data: Uint8Array,
) : JsAny

/** The `harfbuzzjs` face; `referenceTable` returns undefined for a missing table. */
@JsName("Face")
internal external class HarfBuzzFace(
    blob: HarfBuzzBlob,
    index: Int,
) : JsAny {
    fun referenceTable(table: String): Uint8Array?
}

/** The `harfbuzzjs` font; shaping properties such as the scale live on it. */
@JsName("Font")
internal external class HarfBuzzFont(
    face: HarfBuzzFace,
) : JsAny {
    fun setScale(
        xScale: Int,
        yScale: Int,
    )

    fun hExtents(): HarfBuzzFontExtents
}

/** The `harfbuzzjs` font extents, in the font's current scale (26.6 units). */
internal external class HarfBuzzFontExtents : JsAny {
    val ascender: Int
    val descender: Int
    val lineGap: Int
}

/** The `harfbuzzjs` shaping buffer, driven through the explicit direction/script path. */
@JsName("Buffer")
internal external class HarfBuzzBuffer : JsAny {
    fun addCodePoints(
        codePoints: JsArray<JsNumber>,
        itemOffset: Int,
        itemLength: Int,
    )

    fun setDirection(direction: Int)

    fun setScript(script: String)

    fun getGlyphInfos(): JsArray<HarfBuzzGlyphInfo>

    fun getGlyphPositions(): JsArray<HarfBuzzGlyphPosition>
}

internal external interface HarfBuzzGlyphInfo : JsAny {
    val codepoint: Int
    val cluster: Int
}

internal external interface HarfBuzzGlyphPosition : JsAny {
    val xAdvance: Int
    val yAdvance: Int
    val xOffset: Int
    val yOffset: Int
}

/** The `harfbuzzjs` direction enumeration; `LTR` is the engine's `HB_DIRECTION_LTR`. */
@JsName("Direction")
internal external object HarfBuzzDirection : JsAny {
    val LTR: Int
}

@JsName("shape")
internal external fun harfBuzzShape(
    font: HarfBuzzFont,
    buffer: HarfBuzzBuffer,
)
