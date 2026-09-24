@file:JsModule("@zkl2333/freetype-wasm")
@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.text.ttf

import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsModule
import kotlin.js.JsName
import kotlin.js.Promise
import kotlin.js.definedExternally

@JsName("default")
internal external fun initFreeType(options: JsAny? = definedExternally): Promise<FreeType>

/** One initialized module: a wasm instance with its own FreeType library and heap. */
@JsName("FreeType")
internal external class FreeType : JsAny {
    /** Copies [bytes] into the wasm heap; the face stands on that copy, not on the source. */
    fun newFace(
        bytes: Uint8Array,
        faceIndex: Int = definedExternally,
    ): Face
}

/** One font face in the shared module; its wasm heap copy lives until [destroy]. */
@JsName("Face")
internal external class Face : JsAny {
    fun setPixelSize(
        px: Int,
        pyOrZero: Int = definedExternally,
    ): Face

    fun loadGlyph(options: LoadGlyphOptions = definedExternally): LoadedGlyph

    fun destroy()
}

/** One rendered glyph; its bitmap is already copied out of the wasm heap. */
internal external interface LoadedGlyph : JsAny {
    val width: Int
    val rows: Int
    val pitch: Int
    val pixelMode: Int
    val numGrays: Int
    val bitmapLeft: Int
    val bitmapTop: Int
    val buffer: Uint8Array
}

/** The package's `FT` constants; only the gray pixel mode is needed. */
@JsName("FT")
internal external object FreeTypeConstants : JsAny {
    val PIXEL_MODE_GRAY: Int
}
