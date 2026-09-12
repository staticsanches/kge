// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.device.RecordingGpuDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.DefaultRenderer
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The last two renderer ops, `clearBuffer` and `updateViewport`, and a sweep
 * over the whole surface that pins every method is implemented (no stub left).
 */
class RendererSurfaceTest :
    FunSpec({
        fun renderer(recorder: RecordingGLService): DefaultRenderer {
            GLService.override(recorder)
            return DefaultRenderer()
        }

        val color = Pixel.rgba(0x33669980u)

        test("clearBuffer clears the color bit with the color as normalized floats") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            renderer.clearBuffer(color, depth = false)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall(
                        "clearColor",
                        listOf(color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f),
                    ),
                    RecordedGLCall("clear", listOf(GL.COLOR_BUFFER_BIT)),
                )
        }

        test("clearBuffer adds the depth bit when requested") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            renderer.clearBuffer(color, depth = true)

            recorder.calls.last() shouldBe
                RecordedGLCall("clear", listOf(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT))
        }

        test("updateViewport records the viewport") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            renderer.updateViewport(Int2D(3, 4), Int2D(640, 480))

            recorder.calls shouldBe
                listOf(RecordedGLCall("viewport", listOf(3, 4, 640, 480)))
        }

        test("the whole renderer surface is implemented (no stub remains)") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                recorder.clear()
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                    SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                        val decal = Decal(texture, sprite)
                        val instance =
                            DecalInstance(
                                decal = decal,
                                pos = List(4) { Float2D(0f, 0f) },
                                uv = List(4) { Float2D(0f, 0f) },
                                tint = List(4) { Colors.WHITE },
                                mode = Decal.Mode.NORMAL,
                                structure = Decal.Structure.FAN,
                            )

                        renderer.updateTexture(texture, sprite)
                        renderer.readTexture(texture, sprite)
                        renderer.applyTexture(texture)
                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                        renderer.drawDecal(scope, instance)
                        renderer.clearBuffer(Colors.WHITE, depth = true)
                        renderer.updateViewport(Int2D(0, 0), Int2D(8, 8))
                    }
                }

                recorder.calls.map { it.name } shouldBe
                    listOf(
                        "createTexture",
                        "bindTexture",
                        "texParameteri",
                        "texParameteri",
                        "texParameteri",
                        "texParameteri",
                        "texImage2D",
                        "bindTexture",
                        "texSubImage2D",
                        "bindTexture",
                        "getTexImage",
                        "bindTexture",
                        "disable",
                        "bindBuffer",
                        "bufferData",
                        "bindBuffer",
                        "bufferSubData",
                        "drawArrays",
                        "disable",
                        "blendFunc",
                        "bindTexture",
                        "bindBuffer",
                        "bufferSubData",
                        "drawArrays",
                        "clearColor",
                        "clear",
                        "viewport",
                        "deleteTexture",
                    )
            }
        }
    })
