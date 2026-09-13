package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Float2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [DecalInstance]: it carries the decal/mode/structure plus the [VerticesInfo]
 * geometry and derives the vertex count from it; per-vertex reads pass through
 * to the geometry.
 */
class DecalInstanceTest :
    FunSpec({
        test("exposes the decal, mode, structure and geometry") {
            withTestDecal { decal ->
                val vertices =
                    verticesOf(
                        pos = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(1f, 1f)),
                        uv = listOf(Float2D(0f, 0f), Float2D(1f, 0f), Float2D(1f, 1f)),
                        tint = List(3) { Colors.WHITE },
                    )

                val instance = DecalInstance(decal, Decal.Mode.NORMAL, Decal.Structure.FAN, vertices)

                instance.decal shouldBe decal
                instance.mode shouldBe Decal.Mode.NORMAL
                instance.structure shouldBe Decal.Structure.FAN
                instance.vertices shouldBe vertices
            }
        }

        test("the vertex count derives from the given geometry") {
            withTestDecal { decal ->
                val instance =
                    DecalInstance(
                        decal,
                        Decal.Mode.ADDITIVE,
                        Decal.Structure.LIST,
                        verticesOf(
                            pos = listOf(Float2D(1f, 2f), Float2D(3f, 4f)),
                            uv = listOf(Float2D(0.25f, 0.5f), Float2D(0.75f, 1f)),
                            tint = List(2) { Colors.WHITE },
                        ),
                    )

                instance.vertexCount shouldBe 2
            }
        }
    })
