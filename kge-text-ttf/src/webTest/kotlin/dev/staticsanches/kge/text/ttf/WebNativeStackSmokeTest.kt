package dev.staticsanches.kge.text.ttf

import io.kotest.core.spec.style.FunSpec

/**
 * The web native-stack smoke, shared by js and wasmJs: resolving the shared
 * FreeType module initializes the wasm instance. No rasterization.
 */
class WebNativeStackSmokeTest :
    FunSpec({
        test("the FreeType wasm module initializes") {
            freeTypeModule()
        }
    })
