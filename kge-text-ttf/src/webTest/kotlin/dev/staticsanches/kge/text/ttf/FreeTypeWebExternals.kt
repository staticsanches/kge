@file:JsModule("@zkl2333/freetype-wasm")
@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.text.ttf

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsModule
import kotlin.js.JsName
import kotlin.js.Promise
import kotlin.js.definedExternally

@JsName("default")
external fun initFreeType(options: JsAny? = definedExternally): Promise<JsAny>
