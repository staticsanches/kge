@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.text.ttf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * The web native-stack smoke, shared by js and wasmJs: the FreeType wasm module
 * initializes. It is driven through its ESM entry point; no rasterization.
 */
class WebNativeStackSmokeTest :
    FunSpec({
        test("the FreeType wasm module initializes") {
            val freeType: JsAny? = initFreeType().await()
            freeType shouldNotBe null
        }
    })
