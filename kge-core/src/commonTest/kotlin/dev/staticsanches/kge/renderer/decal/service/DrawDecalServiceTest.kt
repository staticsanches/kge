package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.DecalPatch
import dev.staticsanches.kge.renderer.decal.assertVerticesCloseTo
import dev.staticsanches.kge.renderer.decal.withTestDecal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [DrawDecalService]: the full-decal quad and its `DecalPatch` form. Corners
 * follow olc's `DrawDecal` (scaled by the sprite size, y flipped) with a uniform
 * tint; the patch form turns patch coordinates into polygon vertices.
 */
class DrawDecalServiceTest :
    FunSpec({
        test("builds the four vertices with the exact clip-space math and uniform tint") {
            withTestDecal { decal ->
                val instance =
                    DrawDecalService.drawDecal(
                        position = Float2D(16f, 8f),
                        decal = decal,
                        scale = Float2D(2f, 3f),
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
                        Float2D(-0.5f, -0.25f),
                        Float2D(0f, -0.25f),
                        Float2D(0f, 0.5f),
                    ),
                )
                instance.uv shouldBe
                    listOf(Float2D(0f, 0f), Float2D(0f, 1f), Float2D(1f, 1f), Float2D(1f, 0f))
                instance.tint shouldBe List(4) { Colors.WHITE }
            }
        }

        test("carries every mode and structure into the instance") {
            withTestDecal { decal ->
                Decal.Mode.entries.forEach { mode ->
                    Decal.Structure.entries.forEach { structure ->
                        val instance =
                            DrawDecalService.drawDecal(
                                position = Float2D(0f, 0f),
                                decal = decal,
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

        test("the patch form builds olc's executed screen quad and reuses the polygon path") {
            withTestDecal { decal ->
                val patch = decal.patch(Int2D(2, 1), Int2D(4, 2))

                val instance =
                    DrawDecalService.drawDecal(
                        position = Float2D(16f, 8f),
                        patch = patch,
                        scale = Float2D(2f, 1f),
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                        viewport = Int2D(64, 32),
                    )

                instance.decal shouldBe decal
                instance.vertexCount shouldBe 4
                assertVerticesCloseTo(
                    instance.pos,
                    listOf(
                        Float2D(-0.5f, 0.4375f),
                        Float2D(-0.5f, 0.5f),
                        Float2D(-0.4375f, 0.5f),
                        Float2D(-0.4375f, 0.4375f),
                    ),
                )
                assertVerticesCloseTo(
                    instance.uv,
                    listOf(
                        Float2D(0.25f, 0.75f),
                        Float2D(0.25f, 0.25f),
                        Float2D(0.75f, 0.25f),
                        Float2D(0.75f, 0.75f),
                    ),
                )
                instance.tint shouldBe List(4) { Colors.WHITE }
            }
        }

        test("the patch form carries every mode and structure") {
            withTestDecal { decal ->
                val patch = decal.patch(Int2D(0, 0), Int2D(8, 4))

                Decal.Mode.entries.forEach { mode ->
                    Decal.Structure.entries.forEach { structure ->
                        val instance =
                            DrawDecalService.drawDecal(
                                position = Float2D(0f, 0f),
                                patch = patch,
                                scale = Float2D(1f, 1f),
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

        test("the service is overridable in both draw forms") {
            withTestDecal { decal ->
                DrawDecalService.override(
                    object : DrawDecalService {
                        override fun drawDecal(
                            position: Float2D,
                            decal: Decal,
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

                        override fun drawDecal(
                            position: Float2D,
                            patch: DecalPatch,
                            scale: Float2D,
                            mode: Decal.Mode,
                            structure: Decal.Structure,
                            viewport: Int2D,
                        ): DecalInstance =
                            DecalInstance(
                                patch.decal,
                                emptyList(),
                                emptyList(),
                                emptyList(),
                                mode,
                                structure,
                            )
                    },
                )

                val full =
                    DrawDecalService.drawDecal(
                        position = Float2D(16f, 8f),
                        decal = decal,
                        scale = Float2D(2f, 3f),
                        tint = Colors.WHITE,
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                        viewport = Int2D(64, 32),
                    )
                val patched =
                    DrawDecalService.drawDecal(
                        position = Float2D(16f, 8f),
                        patch = decal.patch(Int2D(0, 0), Int2D(8, 4)),
                        scale = Float2D(2f, 3f),
                        mode = Decal.Mode.NORMAL,
                        structure = Decal.Structure.FAN,
                        viewport = Int2D(64, 32),
                    )

                full.vertexCount shouldBe 0
                patched.vertexCount shouldBe 0
            }
        }
    })
