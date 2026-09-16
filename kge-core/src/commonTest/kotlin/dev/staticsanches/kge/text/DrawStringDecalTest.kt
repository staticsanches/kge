package dev.staticsanches.kge.text

import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.assertTints
import dev.staticsanches.kge.renderer.decal.assertUvsCloseTo
import dev.staticsanches.kge.renderer.decal.assertVerticesCloseTo
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4

/**
 * The decal text draw: one partial-cell [DecalInstance] per character, anchored
 * by olc's mono/proportional walk and emitted to the caller's collector with the
 * requested mode and structure — this path never resolves the pixel mode.
 */
class DrawStringDecalTest :
    FunSpec({
        fun newScope(): ResourceScope {
            installGl()
            return ResourceScope().also { DrawStringService.createResources(it) }
        }

        fun installRecorder(): RecordingPartialDecalService {
            val recorder = RecordingPartialDecalService(DrawPartialDecalService.original)
            DrawPartialDecalService.override(recorder)
            return recorder
        }

        test("mono decal cell matches the olc partial-decal geometry") {
            newScope().use { scope ->
                val decal = fontDecal(scope)
                val instances = mutableListOf<DecalInstance>()
                DrawStringService.drawStringDecal(
                    scope,
                    Float2D(0f, 0f),
                    "A",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(128, 48),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) { instances += it }

                instances.size shouldBe 1
                val instance = instances.single()
                instance.decal shouldBe decal
                instance.mode shouldBe Decal.Mode.NORMAL
                instance.structure shouldBe Decal.Structure.FAN
                assertVerticesCloseTo(
                    instance.vertices,
                    listOf(
                        Float2D(-1f, 1f),
                        Float2D(-1f, 0.6666667f),
                        Float2D(-0.8671875f, 0.6666667f),
                        Float2D(-0.8671875f, 1f),
                    ),
                )
                assertUvsCloseTo(
                    instance.vertices,
                    listOf(
                        Float2D(0.06250078f, 0.33333542f),
                        Float2D(0.06250078f, 0.4999979f),
                        Float2D(0.12499922f, 0.4999979f),
                        Float2D(0.12499922f, 0.33333542f),
                    ),
                )
                assertTints(instance.vertices, List(4) { Colors.WHITE })
            }
        }

        test("mono cells read the 8x8 source cell and step by 8*scale") {
            val recorder = installRecorder()
            newScope().use { scope ->
                DrawStringService.drawStringDecal(
                    scope,
                    Float2D(16f, 8f),
                    "Hi",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}

                recorder.calls.map { it.position } shouldBe listOf(Float2D(16f, 8f), Float2D(24f, 8f))
                recorder.calls.map { it.sourcePosition } shouldBe listOf(Float2D(64f, 16f), Float2D(72f, 32f))
                recorder.calls.map { it.sourceSize } shouldBe listOf(Float2D(8f, 8f), Float2D(8f, 8f))
                recorder.calls.map { it.viewport } shouldBe listOf(Int2D(320, 240), Int2D(320, 240))
            }
        }

        test("prop cells use the spacing source column and advance") {
            val recorder = installRecorder()
            newScope().use { scope ->
                DrawStringService.drawStringPropDecal(
                    scope,
                    Float2D(0f, 0f),
                    "Hi",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}

                recorder.calls.map { it.sourcePosition } shouldBe listOf(Float2D(64f, 16f), Float2D(75f, 32f))
                recorder.calls.map { it.sourceSize } shouldBe listOf(Float2D(8f, 8f), Float2D(3f, 8f))
                recorder.calls.map { it.position } shouldBe listOf(Float2D(0f, 0f), Float2D(8f, 0f))
            }
        }

        test("newline and tab advance in scaled cells") {
            val recorder = installRecorder()
            newScope().use { scope ->
                DrawStringService.drawStringDecal(
                    scope,
                    Float2D(0f, 0f),
                    "A\nB",
                    Colors.WHITE,
                    Float2D(2f, 3f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}
                recorder.calls.map { it.position } shouldBe listOf(Float2D(0f, 0f), Float2D(0f, 24f))

                recorder.calls.clear()
                DrawStringService.drawStringDecal(
                    scope,
                    Float2D(0f, 0f),
                    "A\tB",
                    Colors.WHITE,
                    Float2D(2f, 3f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}
                recorder.calls.map { it.position } shouldBe listOf(Float2D(0f, 0f), Float2D(80f, 0f))
            }
        }

        test("the requested mode, structure and tint land in the emitted instances") {
            newScope().use { scope ->
                val decal = fontDecal(scope)
                val instances = mutableListOf<DecalInstance>()
                DrawStringService.drawStringPropDecal(
                    scope,
                    Float2D(0f, 0f),
                    "Hi",
                    Colors.YELLOW,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.ADDITIVE,
                    Decal.Structure.LIST,
                ) { instances += it }

                instances.size shouldBe 2
                instances.forEach { instance ->
                    instance.decal shouldBe decal
                    instance.mode shouldBe Decal.Mode.ADDITIVE
                    instance.structure shouldBe Decal.Structure.LIST
                    assertTints(instance.vertices, List(instance.vertexCount) { Colors.YELLOW })
                }
            }
        }

        test("an empty string emits no instance") {
            newScope().use { scope ->
                val instances = mutableListOf<DecalInstance>()
                DrawStringService.drawStringDecal(
                    scope,
                    Float2D(0f, 0f),
                    "",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) { instances += it }

                instances shouldBe emptyList()
            }
        }

        test("a non-positive tab size does not fail on the decal path") {
            newScope().use { scope ->
                val instances = mutableListOf<DecalInstance>()
                DrawStringService.drawStringDecal(
                    scope,
                    Float2D(0f, 0f),
                    "A\tB",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    0,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) { instances += it }

                instances.size shouldBe 2
            }
        }

        test("a scope without the registered font fails fast, even for empty text") {
            ResourceScope().use { scope ->
                shouldThrow<IllegalStateException> {
                    DrawStringService.drawStringDecal(
                        scope,
                        Float2D(0f, 0f),
                        "",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(320, 240),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) {}
                }
                shouldThrow<IllegalStateException> {
                    DrawStringService.drawStringPropDecal(
                        scope,
                        Float2D(0f, 0f),
                        "",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(320, 240),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) {}
                }
            }
        }
    })

private class PartialDecalCall(
    val position: Float2D,
    val sourcePosition: Float2D,
    val sourceSize: Float2D,
    val scale: Float2D,
    val tint: Pixel,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    val viewport: Int2D,
)

private class RecordingPartialDecalService(
    private val delegate: DrawPartialDecalService,
) : DrawPartialDecalService {
    val calls = mutableListOf<PartialDecalCall>()

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
        calls += PartialDecalCall(position, sourcePosition, sourceSize, scale, tint, mode, structure, viewport)
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
