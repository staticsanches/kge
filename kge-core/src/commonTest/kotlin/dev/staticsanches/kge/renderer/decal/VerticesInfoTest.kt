package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The quad [VerticesInfo]: four vertices in olc's order — top-left, bottom-left,
 * bottom-right, top-right — with `(u0, v0)` at the top-left corner, `(u1, v1)`
 * at the bottom-right one and a uniform tint.
 */
class VerticesInfoTest :
    FunSpec({
        test("builds the four quad vertices in olc's TL/BL/BR/TR order") {
            val vertices =
                VerticesInfo.quad(
                    posX = -0.5f,
                    posY = 0.5f,
                    dimX = 0.25f,
                    dimY = -0.25f,
                    u0 = 0.125f,
                    v0 = 0.25f,
                    u1 = 0.625f,
                    v1 = 0.75f,
                    tint = Colors.WHITE,
                )

            vertices.vertexCount shouldBe 4
            assertVerticesCloseTo(
                vertices,
                listOf(
                    Float2D(-0.5f, 0.5f),
                    Float2D(-0.5f, -0.25f),
                    Float2D(0.25f, -0.25f),
                    Float2D(0.25f, 0.5f),
                ),
            )
            assertUvsCloseTo(
                vertices,
                listOf(
                    Float2D(0.125f, 0.25f),
                    Float2D(0.125f, 0.75f),
                    Float2D(0.625f, 0.75f),
                    Float2D(0.625f, 0.25f),
                ),
            )
        }

        test("tints every vertex uniformly") {
            val tint = Pixel.rgba(10, 20, 30, 40)
            val vertices =
                VerticesInfo.quad(
                    posX = 0f,
                    posY = 0f,
                    dimX = 1f,
                    dimY = 1f,
                    u0 = 0f,
                    v0 = 0f,
                    u1 = 1f,
                    v1 = 1f,
                    tint = tint,
                )

            assertTints(vertices, List(4) { tint })
        }
    })
