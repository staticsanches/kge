package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [DecalInstance]: the vertex count is derived from the position list, and the
 * instance snapshots the parallel per-vertex lists so the geometry can not
 * change under the renderer.
 */
class DecalInstanceTest :
    FunSpec({
        fun withDecal(block: (Decal) -> Unit) {
            GLService.override(RecordingGLService())
            Texture.create(1, 1, GL.NEAREST, GL.CLAMP_TO_EDGE).use { texture ->
                SpriteService.create(1, 1, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    block(Decal(texture, sprite))
                }
            }
        }

        test("the vertex count derives from the position list") {
            withDecal { decal ->
                val instance =
                    DecalInstance(
                        decal = decal,
                        pos = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(1f, 1f)),
                        uv = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(1f, 1f)),
                        tint = List(3) { Pixel.rgba(255, 255, 255) },
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                    )

                instance.vertexCount shouldBe 3
                instance.decal shouldBe decal
                instance.mode shouldBe Decal.Mode.NORMAL
                instance.structure shouldBe Decal.Structure.FAN
            }
        }

        test("the instance snapshots the vertex lists so it is immutable") {
            withDecal { decal ->
                val pos = mutableListOf(Float2D(1f, 2f))
                val uv = mutableListOf(Float2D(0.25f, 0.5f))
                val tint = mutableListOf(Pixel.rgba(10, 20, 30, 40))
                val instance =
                    DecalInstance(
                        decal = decal,
                        pos = pos,
                        uv = uv,
                        tint = tint,
                        mode = Decal.Mode.ADDITIVE,
                        structure = Decal.Structure.LIST,
                    )

                pos += Float2D(9f, 9f)
                uv.clear()
                tint.clear()

                instance.pos shouldBe listOf(Float2D(1f, 2f))
                instance.uv shouldBe listOf(Float2D(0.25f, 0.5f))
                instance.tint shouldBe listOf(Pixel.rgba(10, 20, 30, 40))
                instance.vertexCount shouldBe 1
            }
        }
        test("non-parallel vertex lists are rejected at construction") {
            withDecal { decal ->
                shouldThrow<IllegalArgumentException> {
                    DecalInstance(
                        decal = decal,
                        pos = listOf(Float2D(0f, 0f), Float2D(1f, 0f)),
                        uv = listOf(Float2D(0f, 0f)),
                        tint = List(2) { Pixel.rgba(255, 255, 255) },
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                    )
                }
            }
        }
    })
