// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The raw buffer, vertex-array, attribute, draw and state command seam, plus
 * the `GL` namespace forwarding. FBOs, `multiDrawArrays` and `getError` are
 * out of scope.
 */
class BufferDrawStateTest :
    FunSpec({
        fun recordingService(): RecordingGLService {
            val recorder = RecordingGLService()
            GLService.override(recorder)
            return recorder
        }

        test("the buffer commands record in order with their arguments") {
            val recorder = recordingService()

            BufferService.allocate(16, "vbo").use { storage ->
                val buffer = GLService.createBuffer()
                GLService.bindBuffer(GL.ARRAY_BUFFER, buffer)
                GLService.bufferData(GL.ARRAY_BUFFER, storage.resource, 16, GL.STATIC_DRAW)
                GLService.bufferData(GL.ARRAY_BUFFER, 64, GL.DYNAMIC_DRAW)
                GLService.bufferSubData(GL.ARRAY_BUFFER, 4, storage.resource)
                GLService.bindBuffer(GL.ARRAY_BUFFER, null)
                GLService.deleteBuffer(buffer)

                recorder.lastCreatedBuffer shouldBe buffer
                recorder.calls shouldBe
                    listOf(
                        RecordedGLCall("createBuffer", emptyList()),
                        RecordedGLCall("bindBuffer", listOf(GL.ARRAY_BUFFER, buffer)),
                        RecordedGLCall("bufferData", listOf(GL.ARRAY_BUFFER, storage.resource, 16, GL.STATIC_DRAW)),
                        RecordedGLCall("bufferData", listOf(GL.ARRAY_BUFFER, 64, GL.DYNAMIC_DRAW)),
                        RecordedGLCall("bufferSubData", listOf(GL.ARRAY_BUFFER, 4, storage.resource)),
                        RecordedGLCall("bindBuffer", listOf(GL.ARRAY_BUFFER, null)),
                        RecordedGLCall("deleteBuffer", listOf(buffer)),
                    )
            }
        }

        test("the vertex array and attribute commands record in order with their arguments") {
            val recorder = recordingService()

            val vao = GLService.createVertexArray()
            GLService.bindVertexArray(vao)
            GLService.vertexAttribPointer(0, 4, GL.FLOAT, false, 16, 0)
            GLService.enableVertexAttribArray(0)
            GLService.bindVertexArray(null)
            GLService.deleteVertexArray(vao)

            recorder.lastCreatedVertexArray shouldBe vao
            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("createVertexArray", emptyList()),
                    RecordedGLCall("bindVertexArray", listOf(vao)),
                    RecordedGLCall("vertexAttribPointer", listOf(0, 4, GL.FLOAT, false, 16, 0)),
                    RecordedGLCall("enableVertexAttribArray", listOf(0)),
                    RecordedGLCall("bindVertexArray", listOf(null)),
                    RecordedGLCall("deleteVertexArray", listOf(vao)),
                )
        }

        test("the draw and state commands record in order with their arguments") {
            val recorder = recordingService()

            GLService.enable(GL.BLEND)
            GLService.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
            GLService.disable(GL.DEPTH_TEST)
            GLService.drawArrays(GL.TRIANGLE_STRIP, 0, 4)
            GLService.clearColor(1f, 0.5f, 0f, 1f)
            GLService.clear(GL.COLOR_BUFFER_BIT)
            GLService.viewport(0, 0, 800, 600)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("enable", listOf(GL.BLEND)),
                    RecordedGLCall("blendFunc", listOf(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)),
                    RecordedGLCall("disable", listOf(GL.DEPTH_TEST)),
                    RecordedGLCall("drawArrays", listOf(GL.TRIANGLE_STRIP, 0, 4)),
                    RecordedGLCall("clearColor", listOf(1f, 0.5f, 0f, 1f)),
                    RecordedGLCall("clear", listOf(GL.COLOR_BUFFER_BIT)),
                    RecordedGLCall("viewport", listOf(0, 0, 800, 600)),
                )
        }

        test("the GL namespace forwards the draw/state commands to the overridden service") {
            val recorder = recordingService()

            GL.enable(GL.BLEND)
            GL.blendFunc(GL.SRC_ALPHA, GL.ONE)
            GL.clear(GL.COLOR_BUFFER_BIT)
            GL.viewport(0, 0, 4, 4)
            GL.drawArrays(GL.TRIANGLES, 0, 3)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("enable", listOf(GL.BLEND)),
                    RecordedGLCall("blendFunc", listOf(GL.SRC_ALPHA, GL.ONE)),
                    RecordedGLCall("clear", listOf(GL.COLOR_BUFFER_BIT)),
                    RecordedGLCall("viewport", listOf(0, 0, 4, 4)),
                    RecordedGLCall("drawArrays", listOf(GL.TRIANGLES, 0, 3)),
                )
        }
    })
