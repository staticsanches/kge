package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.assertVerticesCloseTo
import dev.staticsanches.kge.renderer.decal.withTestDecal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [DrawPartialDecalService]: the sub-rectangle quad. The screen corners are
 * quantised to the pixel grid (olc's `floor(+0.5)`/`ceil(±0.5)`) and the UVs
 * carry the `0.0001` epsilon and the sprite-size UV scale.
 */
class DrawPartialDecalServiceTest :
    FunSpec({
        test("applies the source sub-rect with the UV epsilon and pixel quantization") {
            withTestDecal { decal ->
                val instance =
                    DrawPartialDecalService.drawPartialDecal(
                        position = Float2D(16f, 8f),
                        decal = decal,
                        sourcePosition = Float2D(1f, 1f),
                        sourceSize = Float2D(4f, 2f),
                        scale = Float2D(1f, 1f),
                        tint = Colors.WHITE,
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                        viewport = Int2D(64, 32),
                    )

                instance.decal shouldBe decal
                instance.vertexCount shouldBe 4
                assertVerticesCloseTo(
                    instance.pos,
                    listOf(
                        Float2D(-0.5f, 0.5f),
                        Float2D(-0.5f, 0.375f),
                        Float2D(-0.359375f, 0.375f),
                        Float2D(-0.359375f, 0.5f),
                    ),
                )
                assertVerticesCloseTo(
                    instance.uv,
                    listOf(
                        Float2D(0.1250125f, 0.250025f),
                        Float2D(0.1250125f, 0.749975f),
                        Float2D(0.6249875f, 0.749975f),
                        Float2D(0.6249875f, 0.250025f),
                    ),
                )
                instance.tint shouldBe List(4) { Colors.WHITE }
            }
        }

        test("carries every mode and structure into the instance") {
            withTestDecal { decal ->
                Decal.Mode.entries.forEach { mode ->
                    Decal.Structure.entries.forEach { structure ->
                        val instance =
                            DrawPartialDecalService.drawPartialDecal(
                                position = Float2D(0f, 0f),
                                decal = decal,
                                sourcePosition = Float2D(0f, 0f),
                                sourceSize = Float2D(8f, 4f),
                                scale = Float2D(1f, 1f),
                                tint = Colors.WHITE,
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
                DrawPartialDecalService.override(
                    object : DrawPartialDecalService {
                        override fun drawPartialDecal(
                            position: Float2D,
                            decal: Decal,
                            sourcePosition: Float2D,
                            sourceSize: Float2D,
                            scale: Float2D,
                            tint: Pixel,
                            mode: Decal.Mode,
                            structure: Decal.Structure,
                            viewport: Int2D,
                        ): DecalInstance =
                            DecalInstance(
                                decal,
                                emptyList(),
                                emptyList(),
                                emptyList(),
                                mode,
                                structure,
                            )
                    },
                )

                val instance =
                    DrawPartialDecalService.drawPartialDecal(
                        position = Float2D(16f, 8f),
                        decal = decal,
                        sourcePosition = Float2D(1f, 1f),
                        sourceSize = Float2D(4f, 2f),
                        scale = Float2D(1f, 1f),
                        tint = Colors.WHITE,
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                        viewport = Int2D(64, 32),
                    )

                instance.vertexCount shouldBe 0
            }
        }
    })
