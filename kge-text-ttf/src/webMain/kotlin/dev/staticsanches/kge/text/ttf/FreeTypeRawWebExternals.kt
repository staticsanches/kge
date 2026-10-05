@file:Suppress("ktlint:standard:filename")

package dev.staticsanches.kge.text.ttf

import org.khronos.webgl.Int32Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsName

/**
 * The shape of the raw Emscripten module `ft.module`; its `_`-prefixed exports
 * are minified-build internals, so a dependency bump must re-verify them.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal external interface FreeTypeRawModule : JsAny {
    /** Emscripten's 32-bit heap view; it is reassigned whenever the heap grows. */
    @JsName("HEAP32")
    val heap32: Int32Array

    @JsName("_malloc")
    fun malloc(size: Int): Int

    @JsName("_free")
    fun free(pointer: Int)

    /** `FT_Set_Var_Design_Coordinates`: [coords] is a pointer to `numCoords` 16.16 ints. */
    @JsName("_FT_Set_Var_Design_Coordinates")
    fun setVarDesignCoordinates(
        face: Int,
        numCoords: Int,
        coords: Int,
    ): Int
}
