package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.assertTints
import dev.staticsanches.kge.renderer.decal.assertUvsCloseTo
import dev.staticsanches.kge.renderer.decal.assertVerticesCloseTo
import dev.staticsanches.kge.renderer.decal.emptyVertices
import dev.staticsanches.kge.renderer.decal.withTestDecal
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [DrawPolygonDecalService]: an arbitrary N-vertex textured polygon. Vertices
 * convert to clip space (y flipped); UVs and tints are taken per vertex as given
 * (olc's `DrawPolygonDecal`).
 */
class DrawPolygonDecalServiceTest :
    FunSpec({
        test("builds N vertices with per-vertex UV and tint") {
            withTestDecal { decal ->
                val verts = listOf(Float2D(0f, 0f), Float2D(64f, 0f), Float2D(32f, 32f))
                val uvs = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0.5f, 1f))
                val tints = listOf(Pixel.rgba(255, 0, 0), Pixel.rgba(0, 255, 0), Pixel.rgba(0, 0, 255))

                val instance =
                    DrawPolygonDecalService.drawPolygonDecal(
                        decal = decal,
                        pos = verts,
                        uv = uvs,
                        tint = tints,
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.LIST,
                        viewport = Int2D(64, 32),
                    )

                instance.decal shouldBe decal
                instance.vertexCount shouldBe 3
                assertVerticesCloseTo(
                    instance.vertices,
                    listOf(Float2D(-1f, 1f), Float2D(1f, 1f), Float2D(0f, -1f)),
                )
                assertUvsCloseTo(instance.vertices, uvs)
                assertTints(instance.vertices, tints)
            }
        }

        test("snapshots the caller's vertices so a later mutation cannot change the instance") {
            withTestDecal { decal ->
                val verts = mutableListOf(Float2D(0f, 0f), Float2D(64f, 0f), Float2D(32f, 32f))
                val uvs = mutableListOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0.5f, 1f))
                val tints =
                    mutableListOf(
                        Pixel.rgba(255, 0, 0),
                        Pixel.rgba(0, 255, 0),
                        Pixel.rgba(0, 0, 255),
                    )

                val instance =
                    DrawPolygonDecalService.drawPolygonDecal(
                        decal = decal,
                        pos = verts,
                        uv = uvs,
                        tint = tints,
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.LIST,
                        viewport = Int2D(64, 32),
                    )

                verts[0] = Float2D(9f, 9f)
                uvs.clear()
                tints.clear()

                instance.vertexCount shouldBe 3
                assertVerticesCloseTo(
                    instance.vertices,
                    listOf(Float2D(-1f, 1f), Float2D(1f, 1f), Float2D(0f, -1f)),
                )
                assertUvsCloseTo(
                    instance.vertices,
                    listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(0.5f, 1f)),
                )
                assertTints(
                    instance.vertices,
                    listOf(Pixel.rgba(255, 0, 0), Pixel.rgba(0, 255, 0), Pixel.rgba(0, 0, 255)),
                )
            }
        }

        test("non-parallel pos/uv/tint sizes are rejected at construction") {
            withTestDecal { decal ->
                shouldThrow<IllegalArgumentException> {
                    DrawPolygonDecalService.drawPolygonDecal(
                        decal = decal,
                        pos = listOf(Float2D(0f, 0f), Float2D(64f, 0f)),
                        uv = listOf(Float2D(0f, 0f)),
                        tint = List(2) { Pixel.rgba(255, 255, 255) },
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.LIST,
                        viewport = Int2D(64, 32),
                    )
                }
            }
        }

        test("carries every mode and structure into the instance") {
            withTestDecal { decal ->
                val verts = listOf(Float2D(0f, 0f), Float2D(64f, 0f))
                val uvs = listOf(Float2D(0f, 0f), Float2D(1f, 0f))
                val tints = List(2) { Pixel.rgba(255, 255, 255) }

                Decal.Mode.entries.forEach { mode ->
                    Decal.Structure.entries.forEach { structure ->
                        val instance =
                            DrawPolygonDecalService.drawPolygonDecal(
                                decal = decal,
                                pos = verts,
                                uv = uvs,
                                tint = tints,
                                mode = mode,
                                structure = structure,
                                viewport = Int2D(64, 32),
                            )

                        instance.mode shouldBe mode
                        instance.structure shouldBe structure
                    }
                }
            }
        }

        test("the service is overridable") {
            withTestDecal { decal ->
                DrawPolygonDecalService.override(
                    object : DrawPolygonDecalService {
                        override fun drawPolygonDecal(
                            decal: Decal,
                            pos: List<Float2D>,
                            uv: List<Float2D>,
                            tint: List<Pixel>,
                            mode: Decal.Mode,
                            structure: Decal.Structure,
                            viewport: Int2D,
                        ): DecalInstance = DecalInstance(decal, mode, structure, emptyVertices())
                    },
                )

                val instance =
                    DrawPolygonDecalService.drawPolygonDecal(
                        decal = decal,
                        pos = listOf(Float2D(0f, 0f)),
                        uv = listOf(Float2D(0f, 0f)),
                        tint = listOf(Pixel.rgba(255, 255, 255)),
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.LIST,
                        viewport = Int2D(64, 32),
                    )

                instance.vertexCount shouldBe 0
            }
        }
    })
