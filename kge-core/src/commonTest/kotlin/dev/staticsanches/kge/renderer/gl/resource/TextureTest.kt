// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.gl.resource

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.LeakReporterService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The [Texture] resource: creation pins the raw GL setup, and the T1 lifecycle
 * holds — idempotent close, fail-fast use-after-close, no leak on failure.
 */
class TextureTest :
    FunSpec({
        fun recordingService(): RecordingGLService {
            val recorder = RecordingGLService()
            GLService.override(recorder)
            return recorder
        }

        test("create binds, sets filter and wrap, and allocates RGBA8 storage") {
            val recorder = recordingService()

            Texture.create(4, 2, GL.LINEAR, GL.CLAMP_TO_EDGE, "layer").use {
                val handle = recorder.lastCreatedTexture
                recorder.calls shouldBe
                    listOf(
                        RecordedGLCall("createTexture", emptyList()),
                        RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, handle)),
                        RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)),
                        RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)),
                        RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)),
                        RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)),
                        RecordedGLCall(
                            "texImage2D",
                            listOf(GL.TEXTURE_2D, 0, GL.RGBA, 4, 2, 0, GL.RGBA, GL.UNSIGNED_BYTE, null),
                        ),
                    )
            }

            recorder.calls.last() shouldBe
                RecordedGLCall("deleteTexture", listOf(recorder.lastCreatedTexture))
        }

        test("update binds the texture and uploads the sprite pixels") {
            val recorder = recordingService()

            Texture.create(4, 2, GL.NEAREST, GL.REPEAT).use { texture ->
                val handle = recorder.lastCreatedTexture
                SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    recorder.clear()
                    texture.update(sprite)

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

        test("read binds the texture and reads the pixels back into the sprite") {
            val recorder = recordingService()

            Texture.create(4, 2, GL.NEAREST, GL.CLAMP_TO_EDGE).use { texture ->
                val handle = recorder.lastCreatedTexture
                SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    recorder.clear()
                    texture.read(sprite)

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

        test("apply binds the texture") {
            val recorder = recordingService()

            Texture.create(2, 2, GL.NEAREST, GL.CLAMP_TO_EDGE).use { texture ->
                recorder.clear()
                texture.apply()

                recorder.calls shouldBe
                    listOf(RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, recorder.lastCreatedTexture)))
            }
        }

        test("close deletes the texture exactly once") {
            val recorder = recordingService()
            val texture = Texture.create(2, 2, GL.NEAREST, GL.CLAMP_TO_EDGE)

            texture.close()
            texture.close()

            recorder.calls.count { it.name == "deleteTexture" } shouldBe 1
        }

        test("using a closed texture fails fast") {
            recordingService()
            val texture = Texture.create(2, 2, GL.NEAREST, GL.CLAMP_TO_EDGE)

            texture.close()

            shouldThrow<IllegalStateException> { texture.apply() }
        }

        test("a failure while configuring the texture deletes it") {
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

            shouldThrow<IllegalStateException> {
                Texture.create(2, 2, GL.NEAREST, GL.CLAMP_TO_EDGE, "failing")
            } shouldBe failure

            val handle = recorder.lastCreatedTexture
            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("createTexture", emptyList()),
                    RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, handle)),
                    RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)),
                    RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)),
                    RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)),
                    RecordedGLCall("texParameteri", listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)),
                    RecordedGLCall("deleteTexture", listOf(handle)),
                )
        }

        test("an unclosed texture is reported on collection, not deleted") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )
            val recorder = recordingService()
            val texture = Texture.create(2, 2, GL.NEAREST, GL.REPEAT, "leaky")

            texture.onCollectionObserved()

            reports.single() shouldContain "leaky"
            recorder.calls.none { it.name == "deleteTexture" } shouldBe true
        }
    })
