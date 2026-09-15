package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.engine.WindowInfo
import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.engine.layer.LayerStack
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.assertTints
import dev.staticsanches.kge.renderer.decal.assertUvsCloseTo
import dev.staticsanches.kge.renderer.decal.assertVerticesCloseTo
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The decal addons: default methods that build a `DecalInstance` through the
 * decal services and queue it on the target layer; the render step draws it, the
 * addon never does.
 */
class DecalAddonsTest :
    FunSpec({
        fun withHost(block: (RecordingGLService, DecalAddonHost, Decal) -> Unit) {
            val gl = installGl()
            LayerStack(8, 4).use { stack ->
                stack.createLayer()
                val host = DecalAddonHost(stack, WindowInfo(WindowConfig(64, 32)))
                SpriteService.create(8, 4, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { decal ->
                        block(gl, host, decal)
                    }
                }
            }
        }

        context("DrawDecalAddon") {
            test("drawDecal queues one instance with the current modes and the screen-size viewport") {
                withHost { _, host, decal ->
                    host.decalMode = Decal.Mode.ADDITIVE
                    host.decalStructure = Decal.Structure.LIST

                    host.drawDecal(Float2D(16f, 8f), decal, Float2D(2f, 3f), Colors.RED)

                    val queued = host.layers.target.decalInstances
                    queued.size shouldBe 1
                    val instance = queued.single()
                    instance.decal shouldBe decal
                    instance.mode shouldBe Decal.Mode.ADDITIVE
                    instance.structure shouldBe Decal.Structure.LIST
                    assertVerticesCloseTo(
                        instance.vertices,
                        listOf(
                            Float2D(-0.5f, 0.5f),
                            Float2D(-0.5f, -0.25f),
                            Float2D(0f, -0.25f),
                            Float2D(0f, 0.5f),
                        ),
                    )
                    assertTints(instance.vertices, List(4) { Colors.RED })
                }
            }

            test("drawDecal defaults to a one-by-one scale and a white tint") {
                withHost { _, host, decal ->
                    host.drawDecal(Float2D(0f, 0f), decal)

                    val queued = host.layers.target.decalInstances
                    val instance = queued.single()
                    assertVerticesCloseTo(
                        instance.vertices,
                        listOf(
                            Float2D(-1f, 1f),
                            Float2D(-1f, 0.75f),
                            Float2D(-0.75f, 0.75f),
                            Float2D(-0.75f, 1f),
                        ),
                    )
                    assertTints(instance.vertices, List(4) { Colors.WHITE })
                }
            }

            test("the addon only queues the instance, issuing no draw command") {
                withHost { gl, host, decal ->
                    gl.clear()

                    host.drawDecal(Float2D(16f, 8f), decal)

                    val layer = host.layers.target
                    layer.decalInstances.size shouldBe 1
                    gl.calls shouldBe emptyList()
                    layer.target.get(0, 0) shouldBe Colors.TRANSPARENT
                    layer.update shouldBe false
                }
            }
        }

        context("DrawPartialDecalAddon") {
            test("drawPartialDecal forwards the source region, modes and screen-size viewport") {
                withHost { _, host, decal ->
                    host.decalMode = Decal.Mode.MULTIPLICATIVE
                    host.decalStructure = Decal.Structure.STRIP

                    host.drawPartialDecal(Float2D(16f, 8f), decal, Float2D(1f, 1f), Float2D(4f, 2f))

                    val queued = host.layers.target.decalInstances
                    val instance = queued.single()
                    instance.decal shouldBe decal
                    instance.mode shouldBe Decal.Mode.MULTIPLICATIVE
                    instance.structure shouldBe Decal.Structure.STRIP
                    assertVerticesCloseTo(
                        instance.vertices,
                        listOf(
                            Float2D(-0.5f, 0.5f),
                            Float2D(-0.5f, 0.375f),
                            Float2D(-0.359375f, 0.375f),
                            Float2D(-0.359375f, 0.5f),
                        ),
                    )
                    assertUvsCloseTo(
                        instance.vertices,
                        listOf(
                            Float2D(0.1250125f, 0.250025f),
                            Float2D(0.1250125f, 0.749975f),
                            Float2D(0.6249875f, 0.749975f),
                            Float2D(0.6249875f, 0.250025f),
                        ),
                    )
                    assertTints(instance.vertices, List(4) { Colors.WHITE })
                }
            }
        }
    })

private class DecalAddonHost(
    override val layers: LayerStack,
    override val window: WindowInfo,
) : HasLayers,
    HasWindow,
    HasDrawModes,
    DrawDecalAddon,
    DrawPartialDecalAddon {
    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal

    override var decalMode: Decal.Mode = Decal.Mode.NORMAL

    override var decalStructure: Decal.Structure = Decal.Structure.FAN

    override var suspendTextureTransfer: Boolean = false
}
