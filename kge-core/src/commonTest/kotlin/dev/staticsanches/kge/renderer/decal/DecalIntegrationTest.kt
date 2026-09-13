// Handles are `web.gl` interop types on web targets; the recording assertions carry them.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.decal.service.DrawDecalService
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
 * The decal → renderer → GL contract end to end: `Decal` creation through the
 * overridable `Renderer` (texture + initial upload), a draw service building the
 * `DecalInstance`, and `Renderer.drawDecal` recording the sequence — bind,
 * per-mode blend, per-structure primitive, one draw.
 */
class DecalIntegrationTest :
    FunSpec({
        test("a decal is created, drawn through the service and rendered end to end") {
            val recorder = RecordingGLService()
            GLService.override(recorder)
            val renderer = DefaultRenderer()
            Renderer.override(renderer)
            val scope = ResourceScope()
            renderer.createResources(RecordingGpuDevice(), scope)
            recorder.clear()
            try {
                SpriteService.create(4, 4, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.clear(Pixel.rgba(0x3366CCFFu))
                    Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { decal ->
                        val handle = recorder.lastCreatedTexture
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
                                "texImage2D",
                            )
                        recorder.calls.last() shouldBe
                            RecordedGLCall(
                                "texImage2D",
                                listOf(GL.TEXTURE_2D, 0, GL.RGBA, 4, 4, 0, GL.RGBA, GL.UNSIGNED_BYTE, sprite.buffer),
                            )

                        renderer.prepareDrawing(scope)
                        // Grow the staging buffer so the decal draw reuses it.
                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                        recorder.clear()

                        val instance =
                            DrawDecalService.drawDecal(
                                position = Float2D(0f, 0f),
                                decal = decal,
                                scale = Float2D(1f, 1f),
                                tint = Colors.WHITE,
                                mode = Decal.Mode.ADDITIVE,
                                structure = Decal.Structure.FAN,
                                viewport = Int2D(4, 4),
                            )
                        renderer.drawDecal(scope, instance)

                        recorder.calls.map { it.name } shouldBe
                            listOf("disable", "blendFunc", "bindTexture", "bindBuffer", "bufferData", "drawArrays")
                        recorder.calls[1].arguments shouldBe listOf(GL.SRC_ALPHA, GL.ONE)
                        recorder.calls[2].arguments shouldBe listOf(GL.TEXTURE_2D, handle)
                        recorder.calls[3].arguments shouldBe listOf(GL.ARRAY_BUFFER, recorder.lastCreatedBuffer)
                        recorder.calls[5].arguments shouldBe listOf(GL.TRIANGLE_FAN, 0, 4)
                    }
                }
            } finally {
                scope.close()
            }
        }
    })
