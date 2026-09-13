@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.renderer.device.WebGlTestDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The real-GL smoke for the web platform default ([GLService] over the installed
 * WebGL2 context): clear the canvas and read a pixel back into an engine buffer.
 * Runs on both browser targets (js + wasmJs).
 */
class WebGLSmokeTest :
    FunSpec({
        test("the web default clears the canvas and reads a pixel back") {
            WebGlTestDevice.create(width = 8, height = 8).use { device ->
                device.makeCurrent()

                GLService.clearColor(1f, 0f, 0f, 1f)
                GLService.clear(GL.COLOR_BUFFER_BIT)

                BufferService.allocate(4, "gl smoke").use { pixel ->
                    GLService.readPixels(0, 0, 1, 1, GL.RGBA, GL.UNSIGNED_BYTE, pixel.resource)
                    pixel.resource.byteAt(0) shouldBe 0xFF
                    pixel.resource.byteAt(1) shouldBe 0x00
                    pixel.resource.byteAt(2) shouldBe 0x00
                    pixel.resource.byteAt(3) shouldBe 0xFF
                }
            }
        }
    })
