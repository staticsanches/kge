// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.renderer.device.RecordingGpuDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.DefaultRenderer
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The common [Renderer] default: [Renderer.createResources] builds the built-in
 * quad once, `prepareDrawing` sets the 2D frame state in order through the
 * overridable [GLService], and closing the scope releases the quad.
 */
class RendererTest :
    FunSpec({
        fun renderer(recorder: RecordingGLService): DefaultRenderer {
            GLService.override(recorder)
            return DefaultRenderer()
        }

        test("prepareDrawing sets the frame state in order") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                recorder.clear()
                renderer.prepareDrawing(scope)

                val program = recorder.lastCreatedProgram
                val vao = recorder.lastCreatedVertexArray
                recorder.calls shouldBe
                    listOf(
                        RecordedGLCall("enable", listOf(GL.BLEND)),
                        RecordedGLCall("blendFunc", listOf(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)),
                        RecordedGLCall("useProgram", listOf(program)),
                        RecordedGLCall("bindVertexArray", listOf(vao)),
                    )
            }
        }

        test("the built-in program and staging buffer are built once, not per frame") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                renderer.prepareDrawing(scope)
            }

            recorder.calls.count { it.name == "createProgram" } shouldBe 1
            recorder.calls.count { it.name == "createShader" } shouldBe 2
            recorder.calls.count { it.name == "linkProgram" } shouldBe 1
            recorder.calls.count { it.name == "createVertexArray" } shouldBe 1
            recorder.calls.count { it.name == "createBuffer" } shouldBe 1
            recorder.calls.count { it.name == "useProgram" } shouldBe 2
            // Once while the VAO binds its attributes, once per prepareDrawing.
            recorder.calls.count { it.name == "bindVertexArray" } shouldBe 4
            recorder.calls.count { it.name == "enable" } shouldBe 2
            recorder.calls.count { it.name == "blendFunc" } shouldBe 2
        }

        test("closing the scope releases the built-in resources once and a fresh scope rebuilds them") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                recorder.clear()

                scope.close()
                scope.close()

                recorder.calls.count { it.name == "deleteProgram" } shouldBe 1
                recorder.calls.count { it.name == "deleteVertexArray" } shouldBe 1
                recorder.calls.count { it.name == "deleteBuffer" } shouldBe 1
            }

            recorder.clear()
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)

                recorder.calls.count { it.name == "createProgram" } shouldBe 1
                recorder.calls.count { it.name == "createVertexArray" } shouldBe 1
                recorder.calls.count { it.name == "createBuffer" } shouldBe 1
            }
        }

        test("closing the scope makes the device context current before releasing") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            val device = RecordingGpuDevice()
            val scope = ResourceScope()
            renderer.createResources(device, scope)
            recorder.clear()

            scope.close()

            device.calls shouldBe listOf("makeCurrent")
            recorder.calls.count { it.name == "deleteProgram" } shouldBe 1
        }

        test("createResources releases the built quad when the scope rejects the registration") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            val scope = ResourceScope()
            scope.close()

            shouldThrow<IllegalStateException> {
                renderer.createResources(RecordingGpuDevice(), scope)
            }

            recorder.calls.count { it.name == "deleteProgram" } shouldBe 1
            recorder.calls.count { it.name == "deleteVertexArray" } shouldBe 1
            recorder.calls.count { it.name == "deleteBuffer" } shouldBe 1
        }
    })
