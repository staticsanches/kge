@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.text.ttf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * The web native-stack smoke, shared by js and wasmJs: the HarfBuzz wasm module
 * reports a version and the FreeType wasm module initializes. Both are driven
 * through their ESM entry points; no shaping or rasterization.
 */
class WebNativeStackSmokeTest :
    FunSpec({
        test("the HarfBuzz wasm module reports a version") {
            versionString().shouldNotBeBlank()
        }

        test("the FreeType wasm module initializes") {
            val freeType: JsAny? = initFreeType().await()
            freeType shouldNotBe null
        }
    })
