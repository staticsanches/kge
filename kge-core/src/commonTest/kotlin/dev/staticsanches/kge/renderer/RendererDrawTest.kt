// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.verticesOf
import dev.staticsanches.kge.renderer.device.RecordingGpuDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.DefaultRenderer
import dev.staticsanches.kge.renderer.internal.VertexLayout
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun ByteBuffer.floatAt(byteOffset: Int): Float = Float.fromBits(getInt(byteOffset))

/**
 * `drawLayerQuad` builds the full-screen strip from `offset`/`scale`/`tint`;
 * `drawDecal` maps the olc blend/primitive vocabulary and uploads the
 * instance's vertices through the reused staging buffer.
 */
class RendererDrawTest :
    FunSpec({
        fun renderer(recorder: RecordingGLService): DefaultRenderer {
            GLService.override(recorder)
            return DefaultRenderer()
        }

        fun Texture.decalWith(sprite: Sprite): Decal = Decal(this, sprite)

        fun instance(
            decal: Decal,
            mode: Decal.Mode,
            structure: Decal.Structure,
            vertices: Int = 4,
        ): DecalInstance =
            DecalInstance(
                decal = decal,
                mode = mode,
                structure = structure,
                vertices =
                    verticesOf(
                        pos = List(vertices) { Float2D(it.toFloat(), -it.toFloat()) },
                        uv = List(vertices) { Float2D(it.toFloat() * 0.5f, it.toFloat() * 0.25f) },
                        tint = List(vertices) { Colors.WHITE },
                    ),
            )

        test("drawLayerQuad records the strip with the offset/scale UVs and the tint") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                // Grow the staging buffer so this draw reuses it.
                renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                recorder.clear()

                val offset = Float2D(0.25f, 0.5f)
                val scale = Float2D(2f, 3f)
                renderer.drawLayerQuad(scope, offset, scale, Colors.WHITE)

                val vbo = recorder.lastCreatedBuffer
                recorder.calls shouldBe
                    listOf(
                        RecordedGLCall("disable", listOf(GL.CULL_FACE)),
                        RecordedGLCall("bindBuffer", listOf(GL.ARRAY_BUFFER, vbo)),
                        RecordedGLCall(
                            "bufferData",
                            listOf(GL.ARRAY_BUFFER, recorder.bufferDataData(), 4 * VertexLayout.BYTES, GL.STREAM_DRAW),
                        ),
                        RecordedGLCall("drawArrays", listOf(GL.TRIANGLE_STRIP, 0, 4)),
                    )

                val data = recorder.bufferDataData()
                data.vertex(0) shouldBe listOf(-1f, -1f, 1f, 0f, 0.25f, 3.5f, Colors.WHITE.nativeRGBA)
                data.vertex(1) shouldBe listOf(1f, -1f, 1f, 0f, 2.25f, 3.5f, Colors.WHITE.nativeRGBA)
                data.vertex(2) shouldBe listOf(-1f, 1f, 1f, 0f, 0.25f, 0.5f, Colors.WHITE.nativeRGBA)
                data.vertex(3) shouldBe listOf(1f, 1f, 1f, 0f, 2.25f, 0.5f, Colors.WHITE.nativeRGBA)
            }
        }

        test("every Decal.Mode maps to the exact olc blend pair") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                    SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                        val decal = texture.decalWith(sprite)
                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)

                        Decal.Mode.entries.forEach { mode ->
                            recorder.clear()
                            renderer.drawDecal(scope, instance(decal, mode, Decal.Structure.LIST))

                            val expected =
                                when (mode) {
                                    Decal.Mode.NORMAL -> listOf(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
                                    Decal.Mode.ADDITIVE -> listOf(GL.SRC_ALPHA, GL.ONE)
                                    Decal.Mode.MULTIPLICATIVE -> listOf(GL.DST_COLOR, GL.ONE_MINUS_SRC_ALPHA)
                                    Decal.Mode.STENCIL -> listOf(GL.ZERO, GL.SRC_ALPHA)
                                    Decal.Mode.ILLUMINATE -> listOf(GL.ONE_MINUS_SRC_ALPHA, GL.SRC_ALPHA)
                                    Decal.Mode.WIREFRAME -> listOf(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
                                }
                            recorder.calls.first { it.name == "blendFunc" }.arguments shouldBe expected
                        }
                    }
                }
            }
        }

        test("every Decal.Structure maps to the exact primitive") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                    SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                        val decal = texture.decalWith(sprite)
                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)

                        Decal.Structure.entries.forEach { structure ->
                            recorder.clear()
                            renderer.drawDecal(scope, instance(decal, Decal.Mode.NORMAL, structure))

                            val expected =
                                when (structure) {
                                    Decal.Structure.LINE -> GL.LINES
                                    Decal.Structure.FAN -> GL.TRIANGLE_FAN
                                    Decal.Structure.STRIP -> GL.TRIANGLE_STRIP
                                    Decal.Structure.LIST -> GL.TRIANGLES
                                }
                            recorder.calls.last { it.name == "drawArrays" }.arguments shouldBe
                                listOf(expected, 0, 4)
                        }

                        recorder.clear()
                        renderer.drawDecal(scope, instance(decal, Decal.Mode.WIREFRAME, Decal.Structure.FAN))
                        recorder.calls.last { it.name == "drawArrays" }.arguments shouldBe
                            listOf(GL.LINE_LOOP, 0, 4)
                    }
                }
            }
        }

        test("drawDecal uploads the instance's vertices and binds its texture") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                    SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                        val decal = texture.decalWith(sprite)
                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                        recorder.clear()

                        val vertices = 3
                        renderer.drawDecal(scope, instance(decal, Decal.Mode.ADDITIVE, Decal.Structure.LIST, vertices))

                        val textureHandle = recorder.lastCreatedTexture
                        recorder.calls shouldBe
                            listOf(
                                RecordedGLCall("disable", listOf(GL.CULL_FACE)),
                                RecordedGLCall("blendFunc", listOf(GL.SRC_ALPHA, GL.ONE)),
                                RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, textureHandle)),
                                RecordedGLCall("bindBuffer", listOf(GL.ARRAY_BUFFER, recorder.lastCreatedBuffer)),
                                RecordedGLCall(
                                    "bufferData",
                                    listOf(
                                        GL.ARRAY_BUFFER,
                                        recorder.bufferDataData(),
                                        vertices * VertexLayout.BYTES,
                                        GL.STREAM_DRAW,
                                    ),
                                ),
                                RecordedGLCall("drawArrays", listOf(GL.TRIANGLES, 0, vertices)),
                            )

                        val data = recorder.bufferDataData()
                        List(vertices) { index -> data.vertex(index) } shouldBe
                            List(vertices) { index ->
                                val position = index.toFloat()
                                listOf(
                                    position,
                                    -position,
                                    1f,
                                    0f,
                                    position * 0.5f,
                                    position * 0.25f,
                                    Colors.WHITE.nativeRGBA,
                                )
                            }
                    }
                }
            }
        }

        test("consecutive instances draw in order with no batching") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { first ->
                    renderer.createTexture(2, 2, Decal.Filter.LINEAR, Decal.Wrap.REPEAT).use { second ->
                        SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                            val firstDecal = first.decalWith(sprite)
                            val secondDecal = second.decalWith(sprite)
                            renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                            recorder.clear()

                            renderer.drawDecal(scope, instance(firstDecal, Decal.Mode.NORMAL, Decal.Structure.FAN))
                            renderer.drawDecal(scope, instance(secondDecal, Decal.Mode.ADDITIVE, Decal.Structure.LIST))

                            val firstTexture = recorder.calls.first { it.name == "bindTexture" }.arguments[1]
                            val secondTexture = recorder.calls.last { it.name == "bindTexture" }.arguments[1]
                            recorder.calls.map { it.name } shouldBe
                                listOf(
                                    "disable",
                                    "blendFunc",
                                    "bindTexture",
                                    "bindBuffer",
                                    "bufferData",
                                    "drawArrays",
                                    "disable",
                                    "blendFunc",
                                    "bindTexture",
                                    "bindBuffer",
                                    "bufferData",
                                    "drawArrays",
                                )
                            recorder.calls.filter { it.name == "bindTexture" }.map { it.arguments[1] } shouldBe
                                listOf(firstTexture, secondTexture)
                            recorder.calls.filter { it.name == "drawArrays" }.map { it.arguments[0] } shouldBe
                                listOf(GL.TRIANGLE_FAN, GL.TRIANGLES)
                        }
                    }
                }
            }
        }

        test("the staging buffer is created once and each draw uploads once") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            ResourceScope().use { scope ->
                renderer.createResources(RecordingGpuDevice(), scope)
                renderer.prepareDrawing(scope)
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                    SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                        val decal = texture.decalWith(sprite)

                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                        renderer.drawDecal(scope, instance(decal, Decal.Mode.NORMAL, Decal.Structure.FAN))
                        renderer.drawDecal(scope, instance(decal, Decal.Mode.NORMAL, Decal.Structure.FAN))

                        recorder.calls.count { it.name == "createBuffer" } shouldBe 1
                        recorder.calls.count { it.name == "bufferData" } shouldBe 3
                    }
                }
            }
        }
    })

private fun RecordingGLService.bufferDataData(): ByteBuffer =
    calls.last { it.name == "bufferData" }.arguments[1] as ByteBuffer

private fun ByteBuffer.vertex(index: Int): List<Any> {
    val base = index * VertexLayout.BYTES
    return listOf(
        floatAt(base),
        floatAt(base + 4),
        floatAt(base + 8),
        floatAt(base + 12),
        floatAt(base + 16),
        floatAt(base + 20),
        getInt(base + 24),
    )
}
