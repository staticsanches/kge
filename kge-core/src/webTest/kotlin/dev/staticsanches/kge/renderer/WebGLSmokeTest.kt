@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.renderer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import js.buffer.ArrayBufferLike
import js.typedarrays.Uint8Array
import web.dom.document
import web.gl.COLOR_BUFFER_BIT
import web.gl.ID
import web.gl.RGBA
import web.gl.UNSIGNED_BYTE
import web.gl.WebGL2RenderingContext
import web.html.HTMLCanvasElement
import kotlin.js.ExperimentalWasmJsInterop

/**
 * SPIKE probe (throwaway): is a WebGL2 context available in the Karma
 * ChromeHeadless run (js + wasmJs, shared in `webTest`), and can it clear and
 * read a pixel back? Delete or promote to the C9 harness once the facts are in.
 */
class WebGLSmokeTest :
    FunSpec({
        test("canvas webgl2 context clears and reads a pixel back") {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = 8
            canvas.height = 8

            val gl = canvas.getContext(WebGL2RenderingContext.ID)
            checkNotNull(gl) { "WebGL2 context unavailable" }

            gl.clearColor(1f, 0f, 0f, 1f)
            gl.clear(COLOR_BUFFER_BIT)

            val pixel = Uint8Array<ArrayBufferLike>(4)
            gl.readPixels(0, 0, 1, 1, RGBA, UNSIGNED_BYTE, pixel)

            (pixel[0].toInt() and 0xFF) shouldBe 0xFF
            (pixel[1].toInt() and 0xFF) shouldBe 0x00
            (pixel[2].toInt() and 0xFF) shouldBe 0x00
            (pixel[3].toInt() and 0xFF) shouldBe 0xFF
        }
    })
