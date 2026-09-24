@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.text.ttf

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/** One `loadGlyph` call: the glyph index; every other wrapper option keeps its default. */
internal external interface LoadGlyphOptions : JsAny {
    val index: Int
}
