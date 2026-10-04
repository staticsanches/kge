package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasResourceScope
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.engine.WindowConfig
import dev.staticsanches.kge.engine.WindowInfo
import dev.staticsanches.kge.engine.layer.LayerStack
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.applyClosingIfFailed
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.text.KGECoreFontFamily
import dev.staticsanches.kge.text.KGECoreFontService
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The unified text addon: the host's principal font and tab size drive measure,
 * CPU and decal draws, and one call may name a different configured font.
 */
class TextAddonTest :
    FunSpec({
        suspend fun withHost(
            tabSizeInSpaces: Int = 4,
            windowHeight: Int = 32,
            block: suspend (TextAddonHost, ResourceScope, LayerStack, KGECoreFontFamily) -> Unit,
        ) {
            installGl()
            ResourceScope().use { scope ->
                val family = KGECoreFontService.createResources(scope)
                val font = family.defaultFace.font(scope, 8.fontPx)
                LayerStack(64, 32).use { stack ->
                    val host =
                        TextAddonHost(
                            stack.target.target,
                            stack,
                            WindowInfo(WindowConfig(64, windowHeight)),
                            scope,
                            font,
                            tabSizeInSpaces,
                        )
                    block(host, scope, stack, family)
                }
            }
        }

        fun installTextDecalRecorder(): RecordingTextDecalService {
            val recorder = RecordingTextDecalService(DrawPartialDecalService.original)
            DrawPartialDecalService.override(recorder)
            return recorder
        }

        fun target(): Sprite =
            SpriteService
                .create(64, 32, Pixmap.SampleMode.NORMAL, null)
                .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

        test("measureText uses the host's tabSizeInSpaces") {
            withHost { host, _, _, _ ->
                host.measureText("AB") shouldBe Int2D(16, 8)
                host.measureText("A\tB") shouldBe Int2D(48, 8)
            }
            withHost(tabSizeInSpaces = 2) { host, _, _, _ ->
                host.measureText("A\tB") shouldBe Int2D(32, 8)
            }
        }

        test("the typed and raw drawText forms agree with the font") {
            withHost { host, _, _, _ ->
                target().use { reference ->
                    host.textFont.drawText(reference, 1, 2, "Hi", Colors.RED, 2, host.tabSizeInSpaces, host.pixelMode)

                    target().use { typed ->
                        host.drawTarget = typed
                        host.drawText(Int2D(1, 2), "Hi", Colors.RED, 2)
                        textPainted(typed) shouldBe textPainted(reference)
                    }
                    target().use { raw ->
                        host.drawTarget = raw
                        host.drawText(1, 2, "Hi", Colors.RED, 2)
                        textPainted(raw) shouldBe textPainted(reference)
                    }
                    textPainted(reference).isNotEmpty() shouldBe true
                }
            }
        }

        test("drawText paints the requested color at both overloads of the seam") {
            withHost { host, _, _, _ ->
                target().use { typed ->
                    target().use { raw ->
                        host.drawTarget = typed
                        host.drawText(Int2D(1, 2), "Hi", Colors.RED, 1)
                        host.drawTarget = raw
                        host.drawText(1, 2, "Hi", Colors.BLUE, 1)

                        val typedPainted = paintedPixels(typed)
                        val rawPainted = paintedPixels(raw)

                        rawPainted.keys shouldBe typedPainted.keys
                        typedPainted.keys.isNotEmpty() shouldBe true
                        rawPainted.values.toSet() shouldBe setOf(Colors.BLUE)
                        typedPainted.values.toSet() shouldBe setOf(Colors.RED)
                    }
                }
            }
        }

        test("naming the proportional font draws it, not the principal font") {
            withHost { host, _, _, family ->
                val prop = family.proportional.font(host.resourceScope, 8.fontPx)

                val principalPainted =
                    target().use { sprite ->
                        host.drawTarget = sprite
                        host.drawText(1, 2, "Hii", Colors.WHITE, 1)
                        textPainted(sprite)
                    }
                val rawPainted =
                    target().use { sprite ->
                        host.drawTarget = sprite
                        host.drawText(1, 2, "Hii", Colors.WHITE, 1, font = prop)
                        textPainted(sprite)
                    }
                val typedPainted =
                    target().use { sprite ->
                        host.drawTarget = sprite
                        host.drawText(Int2D(1, 2), "Hii", Colors.WHITE, 1, font = prop)
                        textPainted(sprite)
                    }
                val referencePainted =
                    target().use { sprite ->
                        prop.drawText(sprite, 1, 2, "Hii", Colors.WHITE, 1, host.tabSizeInSpaces, host.pixelMode)
                        textPainted(sprite)
                    }

                referencePainted.isNotEmpty() shouldBe true
                rawPainted shouldBe referencePainted
                typedPainted shouldBe referencePainted
                principalPainted shouldNotBe rawPainted
            }
        }

        test("drawTextDecal queues on the target layer with the viewport, modes and font") {
            withHost(windowHeight = 40) { host, _, stack, family ->
                val recorder = installTextDecalRecorder()
                host.decalMode = Decal.Mode.ADDITIVE
                host.decalStructure = Decal.Structure.LIST

                host.drawTextDecal(Float2D(0f, 0f), "A", Colors.RED, Float2D(2f, 2f))

                stack.target.decalInstances.size shouldBe 1
                val call = recorder.calls.single()
                call.position shouldBe Float2D(0f, 0f)
                call.sourcePosition shouldBe Float2D(8f, 16f)
                call.sourceSize shouldBe Float2D(8f, 8f)
                call.scale shouldBe Float2D(2f, 2f)
                call.tint shouldBe Colors.RED
                call.mode shouldBe Decal.Mode.ADDITIVE
                call.structure shouldBe Decal.Structure.LIST
                call.viewport shouldBe Int2D(64, 40)

                val prop = family.proportional.font(host.resourceScope, 8.fontPx)
                recorder.calls.clear()
                host.drawTextDecal(Float2D(0f, 0f), "i", font = prop)
                recorder.calls.single().sourceSize shouldBe Float2D(3f, 8f)
            }
        }

        test("a null draw target no-ops even with a closed font") {
            installGl()
            ResourceScope().use { scope ->
                val family = KGECoreFontService.createResources(scope)
                val closed = family.defaultFace.font(scope, 8.fontPx).also { it.close() }
                LayerStack(64, 32).use { stack ->
                    val host = TextAddonHost(null, stack, WindowInfo(WindowConfig(64, 32)), scope, closed)
                    val layerTarget = stack.target.target
                    layerTarget.clear(Colors.TRANSPARENT)

                    shouldThrow<IllegalStateException> { closed.measureText("A", 4) }

                    host.drawText(Int2D(0, 0), "A")
                    host.drawText(0, 0, "A")

                    textPainted(layerTarget) shouldBe emptySet()
                }
            }
        }

        test("naming another font changes only that call and textFont keeps the default") {
            withHost { host, _, _, family ->
                val prop = family.proportional.font(host.resourceScope, 8.fontPx)

                host.measureText("Ai") shouldBe Int2D(16, 8)
                host.measureText("Ai", prop) shouldBe Int2D(11, 8)
                host.measureText("Ai") shouldBe Int2D(16, 8)
                host.textFont.face shouldBe family.defaultFace
            }
        }

        test("two fonts are drawn alternately without switching the addon state") {
            withHost { host, _, _, family ->
                val prop = family.proportional.font(host.resourceScope, 8.fontPx)
                val recorder = installTextDecalRecorder()

                host.drawTextDecal(Float2D(0f, 0f), "A")
                host.drawTextDecal(Float2D(0f, 0f), "i", font = prop)
                host.drawTextDecal(Float2D(0f, 0f), "A")

                val sizes = recorder.calls.map { it.sourceSize }
                sizes shouldBe listOf(Float2D(8f, 8f), Float2D(3f, 8f), Float2D(8f, 8f))
                host.textFont.face shouldBe family.defaultFace
            }
        }
    })

private fun textPainted(sprite: Sprite): Set<Pair<Int, Int>> =
    (0 until sprite.height)
        .flatMap { y -> (0 until sprite.width).map { x -> x to y } }
        .filter { (x, y) -> sprite.get(x, y) != Colors.TRANSPARENT }
        .toSet()

private fun paintedPixels(sprite: Sprite): Map<Pair<Int, Int>, Pixel> =
    (0 until sprite.height)
        .flatMap { y -> (0 until sprite.width).map { x -> x to y } }
        .map { (x, y) -> (x to y) to sprite.get(x, y) }
        .filter { (_, pixel) -> pixel != Colors.TRANSPARENT }
        .toMap()

private class TextAddonHost(
    override var drawTarget: Sprite?,
    override val layers: LayerStack,
    override val window: WindowInfo,
    override val resourceScope: ResourceScope,
    override var textFont: KGEFont,
    override var tabSizeInSpaces: Int = 4,
) : HasDrawTarget,
    HasDrawModes,
    HasWindow,
    HasLayers,
    HasResourceScope,
    TextAddon {
    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) = error("not used by the text addon")

    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal

    override var decalMode: Decal.Mode = Decal.Mode.NORMAL

    override var decalStructure: Decal.Structure = Decal.Structure.FAN

    override var suspendTextureTransfer: Boolean = false
}

private class TextDecalCall(
    val position: Float2D,
    val sourcePosition: Float2D,
    val sourceSize: Float2D,
    val scale: Float2D,
    val tint: Pixel,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    val viewport: Int2D,
)

private class RecordingTextDecalService(
    private val delegate: DrawPartialDecalService,
) : DrawPartialDecalService {
    val calls = mutableListOf<TextDecalCall>()

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
    ): DecalInstance {
        calls += TextDecalCall(position, sourcePosition, sourceSize, scale, tint, mode, structure, viewport)
        return delegate.drawPartialDecal(
            position,
            decal,
            sourcePosition,
            sourceSize,
            scale,
            tint,
            mode,
            structure,
            viewport,
        )
    }
}
