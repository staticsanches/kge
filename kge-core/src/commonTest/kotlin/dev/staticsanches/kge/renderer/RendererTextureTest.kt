// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.DefaultRenderer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The renderer's texture ops: it maps the typed `Decal.Filter`/`Decal.Wrap` to
 * the raw GL parameters and wires upload/readback/bind onto the GL layer's
 * public [dev.staticsanches.kge.renderer.gl.resource.Texture] lifecycle. These
 * ops are stateless, so they need no resource scope.
 */
class RendererTextureTest :
    FunSpec({
        fun renderer(recorder: RecordingGLService): DefaultRenderer {
            GLService.override(recorder)
            return DefaultRenderer()
        }

        test("createTexture maps the filter and wrap to the GL parameters") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            val filters =
                listOf(
                    Decal.Filter.NEAREST to GL.NEAREST,
                    Decal.Filter.LINEAR to GL.LINEAR,
                )
            val wraps =
                listOf(
                    Decal.Wrap.CLAMP_TO_EDGE to GL.CLAMP_TO_EDGE,
                    Decal.Wrap.REPEAT to GL.REPEAT,
                )

            filters.forEach { (filter, glFilter) ->
                wraps.forEach { (wrap, glWrap) ->
                    recorder.clear()
                    renderer.createTexture(4, 2, filter, wrap).close()

                    val handle = recorder.lastCreatedTexture
                    recorder.calls shouldBe
                        listOf(
                            RecordedGLCall("createTexture", emptyList()),
                            RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, handle)),
                            RecordedGLCall(
                                "texParameteri",
                                listOf(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, glFilter),
                            ),
                            RecordedGLCall(
                                "texParameteri",
                                listOf(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, glFilter),
                            ),
                            RecordedGLCall(
                                "texParameteri",
                                listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, glWrap),
                            ),
                            RecordedGLCall(
                                "texParameteri",
                                listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, glWrap),
                            ),
                            RecordedGLCall(
                                "texImage2D",
                                listOf(GL.TEXTURE_2D, 0, GL.RGBA, 4, 2, 0, GL.RGBA, GL.UNSIGNED_BYTE, null),
                            ),
                            RecordedGLCall("deleteTexture", listOf(handle)),
                        )
                }
            }
        }

        test("updateTexture binds the texture and uploads the sprite pixels") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            renderer.createTexture(4, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                val handle = recorder.lastCreatedTexture
                SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    recorder.clear()
                    renderer.updateTexture(texture, sprite)

                    recorder.calls shouldBe
                        listOf(
                            RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, handle)),
                            RecordedGLCall(
                                "texSubImage2D",
                                listOf(GL.TEXTURE_2D, 0, 0, 0, 4, 2, GL.RGBA, GL.UNSIGNED_BYTE, sprite.buffer),
                            ),
                        )
                }
            }
        }

        test("readTexture reads the texture pixels back into the sprite") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            renderer.createTexture(4, 2, Decal.Filter.LINEAR, Decal.Wrap.REPEAT).use { texture ->
                val handle = recorder.lastCreatedTexture
                SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    recorder.clear()
                    renderer.readTexture(texture, sprite)

                    recorder.calls shouldBe
                        listOf(
                            RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, handle)),
                            RecordedGLCall(
                                "getTexImage",
                                listOf(GL.TEXTURE_2D, 0, 4, 2, GL.RGBA, GL.UNSIGNED_BYTE, sprite.buffer),
                            ),
                        )
                }
            }
        }

        test("applyTexture binds the texture") {
            val recorder = RecordingGLService()
            val renderer = renderer(recorder)
            renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { texture ->
                recorder.clear()
                renderer.applyTexture(texture)

                recorder.calls shouldBe
                    listOf(RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, recorder.lastCreatedTexture)))
            }
        }

        test("a failure while creating the texture deletes it, leaking nothing") {
            val recorder = RecordingGLService()
            val failure = IllegalStateException("upload failed")
            GLService.override(
                object : GLService by recorder {
                    override fun texImage2D(
                        target: GLenum,
                        level: GLint,
                        internalFormat: GLenum,
                        width: GLsizei,
                        height: GLsizei,
                        border: GLint,
                        format: GLenum,
                        type: GLenum,
                        srcData: ByteBuffer?,
                    ): Unit = throw failure
                },
            )
            val renderer = DefaultRenderer()

            shouldThrow<IllegalStateException> {
                renderer.createTexture(2, 2, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
            } shouldBe failure

            recorder.calls.count { it.name == "deleteTexture" } shouldBe 1
        }
    })
