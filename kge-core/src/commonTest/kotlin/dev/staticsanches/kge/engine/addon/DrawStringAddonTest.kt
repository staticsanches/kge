package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasResourceScope
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.engine.WindowInfo
import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.engine.layer.LayerStack
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.assertTints
import dev.staticsanches.kge.renderer.decal.assertUvsCloseTo
import dev.staticsanches.kge.renderer.decal.assertVerticesCloseTo
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.text.DrawStringService
import dev.staticsanches.kge.text.fontDecal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The bitmap-text addon: mono/proportional text drawn into the draw target or
 * queued on the target layer, over the draw roles and the run's resource scope.
 */
class DrawStringAddonTest :
    FunSpec({
        fun withHost(
            tabSizeInSpaces: Int = 4,
            block: (DrawStringAddonHost, ResourceScope, LayerStack) -> Unit,
        ) {
            installGl()
            ResourceScope().use { scope ->
                DrawStringService.createResources(scope)
                LayerStack(64, 32).use { stack ->
                    val host =
                        DrawStringAddonHost(
                            stack.target.target,
                            stack,
                            WindowInfo(WindowConfig(64, 32)),
                            scope,
                            tabSizeInSpaces,
                        )
                    block(host, scope, stack)
                }
            }
        }

        fun target(): Sprite =
            SpriteService
                .create(64, 32, Pixmap.SampleMode.NORMAL, null)
                .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

        test("drawString paints the mono glyphs into the draw target") {
            withHost { host, _, _ ->
                val target = host.drawTarget!!
                target.clear(Colors.TRANSPARENT)

                host.drawString(Int2D(0, 0), "A")

                target.get(2, 0) shouldBe Colors.WHITE
                target.get(0, 0) shouldBe Colors.TRANSPARENT
                target.get(1, 7) shouldBe Colors.TRANSPARENT
            }
        }

        test("the mono draws forward the mono service, typed and raw") {
            withHost { host, scope, _ ->
                target().use { reference ->
                    DrawStringService.drawString(scope, reference, 1, 2, "Hi", Colors.RED, 2, 4, host.pixelMode)

                    target().use { typed ->
                        host.drawTarget = typed
                        host.drawString(Int2D(1, 2), "Hi", Colors.RED, 2)
                        painted(typed) shouldBe painted(reference)
                    }
                    target().use { raw ->
                        host.drawTarget = raw
                        host.drawString(1, 2, "Hi", Colors.RED, 2)
                        painted(raw) shouldBe painted(reference)
                    }
                }
            }
        }

        test("the prop draws forward the proportional service, typed and raw") {
            withHost { host, scope, _ ->
                target().use { reference ->
                    DrawStringService.drawStringProp(scope, reference, 1, 2, "Ai", Colors.RED, 2, 4, host.pixelMode)

                    target().use { typed ->
                        host.drawTarget = typed
                        host.drawStringProp(Int2D(1, 2), "Ai", Colors.RED, 2)
                        painted(typed) shouldBe painted(reference)
                    }
                    target().use { raw ->
                        host.drawTarget = raw
                        host.drawStringProp(1, 2, "Ai", Colors.RED, 2)
                        painted(raw) shouldBe painted(reference)
                    }
                }
            }
        }

        test("a null draw target returns without drawing") {
            installGl()
            ResourceScope().use { scope ->
                DrawStringService.createResources(scope)
                LayerStack(64, 32).use { stack ->
                    val host = DrawStringAddonHost(null, stack, WindowInfo(WindowConfig(64, 32)), scope)
                    val layerTarget = stack.target.target
                    layerTarget.clear(Colors.TRANSPARENT)

                    host.drawString(Int2D(0, 0), "A")
                    host.drawString(0, 0, "A")
                    host.drawStringProp(Int2D(0, 0), "A")
                    host.drawStringProp(0, 0, "A")

                    painted(layerTarget) shouldBe emptySet()
                }
            }
        }

        test("getTextSize and getTextSizeProp forward tabSizeInSpaces") {
            withHost { host, _, _ ->
                host.getTextSize("AB") shouldBe Int2D(16, 8)
                host.getTextSize("A\tB") shouldBe Int2D(48, 8)
                host.getTextSizeProp("Ai") shouldBe Int2D(11, 8)
                host.getTextSizeProp("A\tB") shouldBe Int2D(48, 8)
            }
            withHost(tabSizeInSpaces = 2) { host, _, _ ->
                host.getTextSize("A\tB") shouldBe Int2D(32, 8)
                host.getTextSizeProp("A\tB") shouldBe Int2D(32, 8)
            }
        }

        test("the tabSizeInSpaces override changes the draw advance") {
            withHost { host, _, _ ->
                val target = host.drawTarget!!
                target.clear(Colors.TRANSPARENT)
                host.drawString(Int2D(0, 0), "A\tB")
                painted(target).any { it.first >= 32 } shouldBe true
            }
            withHost(tabSizeInSpaces = 2) { host, _, _ ->
                val target = host.drawTarget!!
                target.clear(Colors.TRANSPARENT)
                host.drawString(Int2D(0, 0), "A\tB")
                painted(target).none { it.first >= 32 } shouldBe true
            }
        }

        test("drawStringDecal queues the mono cell with the screen-size viewport and the current modes") {
            withHost { host, scope, stack ->
                host.decalMode = Decal.Mode.ADDITIVE
                host.decalStructure = Decal.Structure.LIST

                host.drawStringDecal(Float2D(0f, 0f), "A", Colors.RED)

                val queued = stack.target.decalInstances
                queued.size shouldBe 1
                val instance = queued.single()
                instance.decal shouldBe fontDecal(scope)
                instance.mode shouldBe Decal.Mode.ADDITIVE
                instance.structure shouldBe Decal.Structure.LIST
                assertVerticesCloseTo(
                    instance.vertices,
                    listOf(
                        Float2D(-1f, 1f),
                        Float2D(-1f, 0.5f),
                        Float2D(-0.734375f, 0.5f),
                        Float2D(-0.734375f, 1f),
                    ),
                )
                assertUvsCloseTo(
                    instance.vertices,
                    listOf(
                        Float2D(0.0625008f, 0.3333354f),
                        Float2D(0.0625008f, 0.4999979f),
                        Float2D(0.1249992f, 0.4999979f),
                        Float2D(0.1249992f, 0.3333354f),
                    ),
                )
                assertTints(instance.vertices, List(4) { Colors.RED })
            }
        }

        test("drawStringPropDecal queues the proportional cell") {
            withHost { host, scope, stack ->
                host.drawStringPropDecal(Float2D(8f, 0f), "i")

                val instance = stack.target.decalInstances.single()
                instance.decal shouldBe fontDecal(scope)
                assertVerticesCloseTo(
                    instance.vertices,
                    listOf(
                        Float2D(-0.75f, 1f),
                        Float2D(-0.75f, 0.5f),
                        Float2D(-0.640625f, 0.5f),
                        Float2D(-0.640625f, 1f),
                    ),
                )
                assertUvsCloseTo(
                    instance.vertices,
                    listOf(
                        Float2D(0.5859383f, 0.6666688f),
                        Float2D(0.5859383f, 0.8333312f),
                        Float2D(0.6093742f, 0.8333312f),
                        Float2D(0.6093742f, 0.6666688f),
                    ),
                )
                assertTints(instance.vertices, List(4) { Colors.WHITE })
            }
        }

        test("the decal advance honors the tabSizeInSpaces override") {
            withHost { host, _, stack ->
                host.drawStringDecal(Float2D(0f, 0f), "A\tB")
                val second = stack.target.decalInstances[1]
                second.vertices.x(0) shouldBe 0.25f
            }
            withHost(tabSizeInSpaces = 2) { host, _, stack ->
                host.drawStringDecal(Float2D(0f, 0f), "A\tB")
                val second = stack.target.decalInstances[1]
                second.vertices.x(0) shouldBe -0.25f
            }
        }
    })

private fun painted(sprite: Sprite): Set<Pair<Int, Int>> =
    (0 until sprite.height)
        .flatMap { y -> (0 until sprite.width).map { x -> x to y } }
        .filter { (x, y) -> sprite.get(x, y) != Colors.TRANSPARENT }
        .toSet()

private class DrawStringAddonHost(
    override var drawTarget: Sprite?,
    override val layers: LayerStack,
    override val window: WindowInfo,
    override val resourceScope: ResourceScope,
    override val tabSizeInSpaces: Int = 4,
) : HasDrawTarget,
    HasDrawModes,
    HasWindow,
    HasLayers,
    HasResourceScope,
    DrawStringAddon {
    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = error("not used by the draw addons")

    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal

    override var decalMode: Decal.Mode = Decal.Mode.NORMAL

    override var decalStructure: Decal.Structure = Decal.Structure.FAN

    override var suspendTextureTransfer: Boolean = false
}
