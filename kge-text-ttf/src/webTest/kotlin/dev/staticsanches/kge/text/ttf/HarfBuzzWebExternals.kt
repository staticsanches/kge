@file:JsModule("harfbuzzjs")
@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.text.ttf

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsModule

external fun versionString(): String
