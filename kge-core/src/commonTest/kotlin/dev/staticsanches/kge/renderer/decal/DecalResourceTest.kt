// Handles are `web.gl` interop types on web targets; the recording assertions carry them.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.renderer.gl.GLint
import dev.staticsanches.kge.renderer.gl.GLsizei
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** A recording [Renderer] that delegates to [Renderer.original] and keeps the typed calls. */
private class RecordingRenderer(
    private val delegate: Renderer,
) : Renderer by delegate {
    val calls = mutableListOf<Pair<String, List<Any?>>>()

    override fun createTexture(
        width: Int,
        height: Int,
        filter: Decal.Filter,
        wrap: Decal.Wrap,
        name: String?,
    ): Texture {
        calls += "createTexture" to listOf(width, height, filter, wrap, name)
        return delegate.createTexture(width, height, filter, wrap, name)
    }

    override fun updateTexture(
        texture: Texture,
        sprite: Sprite,
    ) {
        calls += "updateTexture" to listOf(texture, sprite)
        delegate.updateTexture(texture, sprite)
    }

    override fun readTexture(
        texture: Texture,
        sprite: Sprite,
    ) {
        calls += "readTexture" to listOf(texture, sprite)
        delegate.readTexture(texture, sprite)
    }
}

/**
 * The [Decal] resource: creates and owns its [Texture] through the overridable
 * [Renderer], re-uploads/reads back the referenced `Sprite`, and closes the
 * texture exactly once (T1). The `Sprite` is referenced, never owned.
 */
class DecalResourceTest :
    FunSpec({
        fun install(): Pair<RecordingRenderer, RecordingGLService> {
            val gl = RecordingGLService()
            GLService.override(gl)
            val renderer = RecordingRenderer(Renderer.original)
            Renderer.override(renderer)
            return renderer to gl
        }

        test("creation goes through the renderer and performs the initial upload") {
            val (renderer, gl) = install()
            SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.REPEAT).use { decal ->
                    renderer.calls.map { it.first } shouldBe listOf("createTexture", "updateTexture")
                    renderer.calls[0].second shouldBe
                        listOf(4, 2, Decal.Filter.NEAREST, Decal.Wrap.REPEAT, null)
                    renderer.calls[1].second shouldBe listOf(decal.texture, sprite)

                    gl.calls.first() shouldBe RecordedGLCall("createTexture", emptyList())
                    gl.calls.last().name shouldBe "texSubImage2D"
                    gl.calls.last().arguments shouldBe
                        listOf(GL.TEXTURE_2D, 0, 0, 0, 4, 2, GL.RGBA, GL.UNSIGNED_BYTE, sprite.buffer)
                }
            }
        }

        test("update re-uploads the referenced sprite through the renderer") {
            val (renderer, gl) = install()
            SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                Decal(sprite, Decal.Filter.LINEAR, Decal.Wrap.CLAMP_TO_EDGE).use { decal ->
                    val handle = gl.lastCreatedTexture
                    renderer.calls.clear()
                    gl.clear()

                    decal.update()

                    renderer.calls shouldBe listOf("updateTexture" to listOf(decal.texture, sprite))
                    gl.calls shouldBe
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

        test("updateSprite reads the texture back through the renderer") {
            val (renderer, gl) = install()
            SpriteService.create(4, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                Decal(sprite, Decal.Filter.LINEAR, Decal.Wrap.CLAMP_TO_EDGE).use { decal ->
                    val handle = gl.lastCreatedTexture
                    renderer.calls.clear()
                    gl.clear()

                    decal.updateSprite()

                    renderer.calls shouldBe listOf("readTexture" to listOf(decal.texture, sprite))
                    gl.calls shouldBe
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

        test("close deletes the texture exactly once and is idempotent") {
            val (_, gl) = install()
            SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                val decal = Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                gl.clear()

                decal.close()
                decal.close()

                gl.calls.count { it.name == "deleteTexture" } shouldBe 1
            }
        }

        test("using a closed decal fails fast") {
            install()
            SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                val decal = Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                decal.close()

                shouldThrow<IllegalStateException> { decal.update() }
            }
        }

        test("closing the decal does not close the referenced sprite") {
            install()
            SpriteService.create(1, 1, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                val decal = Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                decal.close()

                shouldNotThrow<IllegalStateException> { sprite.get(0, 0) }
            }
        }

        test("a failure during the initial upload frees the texture") {
            val gl = RecordingGLService()
            val failure = IllegalStateException("upload failed")
            GLService.override(
                object : GLService by gl {
                    override fun texSubImage2D(
                        target: GLenum,
                        level: GLint,
                        xOffset: GLint,
                        yOffset: GLint,
                        width: GLsizei,
                        height: GLsizei,
                        format: GLenum,
                        type: GLenum,
                        srcData: ByteBuffer,
                    ): Unit = throw failure
                },
            )
            SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                shouldThrow<IllegalStateException> {
                    Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                } shouldBe failure
            }

            gl.calls.count { it.name == "createTexture" } shouldBe 1
            gl.calls.count { it.name == "deleteTexture" } shouldBe 1
        }
    })
