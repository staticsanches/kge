package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.testsupport.device.WebGlTestDevice
import io.kotest.core.spec.style.FunSpec

/** The web RGBA8 smoke driver: an off-DOM WebGL2 canvas runs the shared coverage assertion. */
class CoverageTextureWebSmokeTest :
    FunSpec({
        test("the RGBA8 coverage region update inks the glyph's box achromatically") {
            WebGlTestDevice.create(width = SMOKE_VIEWPORT_SIZE, height = SMOKE_VIEWPORT_SIZE).use { device ->
                runCoverageTextureSmoke(device)
            }
        }
    })
