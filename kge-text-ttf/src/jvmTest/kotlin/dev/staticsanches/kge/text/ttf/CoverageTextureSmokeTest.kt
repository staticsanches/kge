package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.testsupport.device.GlfwTestDevice
import io.kotest.core.spec.style.FunSpec

private val smokeDevice: GlfwTestDevice? = GlfwTestDevice.detect()

/** The JVM `R8` smoke driver: a hidden GLFW context runs the shared coverage assertion. */
class CoverageTextureSmokeTest :
    FunSpec({
        test("the R8 coverage decal inks the glyph's box achromatically (${smokeDevice?.backend ?: "unavailable"})")
            .config(enabled = smokeDevice != null) {
                smokeDevice!!.use { device -> runCoverageTextureSmoke(device) }
            }
    })
