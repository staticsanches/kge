package dev.staticsanches.kge.text

import dev.staticsanches.kge.annotations.KGESensitiveAPI
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
import dev.staticsanches.kge.testsupport.engine.installGl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4

/**
 * The decal text draw: one partial-cell instance per character, with the
 * requested mode and structure — no pixel mode resolution, no tab validation.
 */
@OptIn(KGESensitiveAPI::class)
class CoreFontDecalTest :
    FunSpec({
        suspend fun withFamily(block: suspend (ResourceScope, KGECoreFontFamily) -> Unit) {
            installGl()
            ResourceScope().use { scope -> block(scope, KGECoreFontService.createResources(scope)) }
        }

        suspend fun KGEFont.Family.mono(
            scope: ResourceScope,
            px: Int = 8,
        ): KGEFont = defaultFace.font(scope, px.fontPx)

        suspend fun KGECoreFontFamily.prop(
            scope: ResourceScope,
            px: Int = 8,
        ): KGEFont = proportional.font(scope, px.fontPx)

        fun installRecorder(): CoreDecalRecorder {
            val recorder = CoreDecalRecorder(DrawPartialDecalService.original)
            DrawPartialDecalService.override(recorder)
            return recorder
        }

        test("mono decal cell matches the olc partial-decal geometry") {
            withFamily { scope, family ->
                val instances = mutableListOf<DecalInstance>()
                family.mono(scope).drawTextDecal(
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
                instance.decal.sprite.width shouldBe 128
                instance.decal.sprite.height shouldBe 48
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

        test("mono cells read the 8x8 source cell and step by 8 * effectiveScale") {
            val recorder = installRecorder()
            withFamily { scope, family ->
                family.mono(scope).drawTextDecal(
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
                val decals = recorder.calls.map { it.decal }.toSet()
                decals.size shouldBe 1
            }
        }

        test("prop cells use the spacing source column and advance") {
            val recorder = installRecorder()
            withFamily { scope, family ->
                family.prop(scope).drawTextDecal(
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
            withFamily { scope, family ->
                val mono = family.mono(scope)
                mono.drawTextDecal(
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
                recorder.calls.map { it.scale } shouldBe listOf(Float2D(2f, 3f), Float2D(2f, 3f))

                recorder.calls.clear()
                mono.drawTextDecal(
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
            withFamily { scope, family ->
                val instances = mutableListOf<DecalInstance>()
                family.prop(scope).drawTextDecal(
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
                    instance.decal.sprite.width shouldBe 128
                    instance.mode shouldBe Decal.Mode.ADDITIVE
                    instance.structure shouldBe Decal.Structure.LIST
                    assertTints(instance.vertices, List(instance.vertexCount) { Colors.YELLOW })
                }
                (instances[0].decal === instances[1].decal) shouldBe true
            }
        }

        test("degenerate decal scales propagate instead of being normalised or rejected") {
            withFamily { scope, family ->
                val mono = family.mono(scope)

                fun emit(scale: Float2D): DecalInstance {
                    val instances = mutableListOf<DecalInstance>()
                    mono.drawTextDecal(
                        Float2D(0f, 0f),
                        "A",
                        Colors.WHITE,
                        scale,
                        TAB_SIZE,
                        Int2D(320, 240),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) { instances += it }
                    instances.size shouldBe 1
                    return instances.single()
                }

                // The zero-width and inverted runs keep the quantised arithmetic of the
                // requested sign and magnitude; a clamp or a sign flip would move them.
                assertVerticesCloseTo(
                    emit(Float2D(0f, 0f)).vertices,
                    listOf(
                        Float2D(-1f, 1f),
                        Float2D(-1f, 1f),
                        Float2D(-0.996875f, 1f),
                        Float2D(-0.996875f, 1f),
                    ),
                )
                assertVerticesCloseTo(
                    emit(Float2D(-2f, 3f)).vertices,
                    listOf(
                        Float2D(-1f, 1f),
                        Float2D(-1f, 0.8f),
                        Float2D(-1.096875f, 0.8f),
                        Float2D(-1.096875f, 1f),
                    ),
                )

                val nan = emit(Float2D(Float.NaN, 1f)).vertices
                nan.x(0) shouldBe -1f
                nan.x(2).isNaN() shouldBe true
                nan.x(3).isNaN() shouldBe true

                val positiveInfinity = emit(Float2D(1f, Float.POSITIVE_INFINITY)).vertices
                positiveInfinity.y(1) shouldBe Float.NEGATIVE_INFINITY
                positiveInfinity.y(2) shouldBe Float.NEGATIVE_INFINITY

                val negativeInfinity = emit(Float2D(1f, Float.NEGATIVE_INFINITY)).vertices
                negativeInfinity.y(1) shouldBe Float.POSITIVE_INFINITY
            }
        }

        test("an empty string emits no instance") {
            withFamily { scope, family ->
                val instances = mutableListOf<DecalInstance>()
                family.mono(scope).drawTextDecal(
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
            withFamily { scope, family ->
                val instances = mutableListOf<DecalInstance>()
                family.mono(scope).drawTextDecal(
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

        test("a closed font fails fast, even for empty text") {
            withFamily { scope, family ->
                val font = family.mono(scope)
                font.close()

                shouldThrow<IllegalStateException> {
                    font.drawTextDecal(
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

        test("the base size multiplies the emitted scale and the newline/tab advances") {
            val recorder = installRecorder()
            withFamily { scope, family ->
                val mono = family.mono(scope, 16)
                mono.drawTextDecal(
                    Float2D(0f, 0f),
                    "Hi",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}
                recorder.calls.map { it.scale } shouldBe listOf(Float2D(2f, 2f), Float2D(2f, 2f))
                recorder.calls.map { it.position } shouldBe listOf(Float2D(0f, 0f), Float2D(16f, 0f))

                recorder.calls.clear()
                mono.drawTextDecal(
                    Float2D(0f, 0f),
                    "A\nB",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}
                recorder.calls.map { it.position } shouldBe listOf(Float2D(0f, 0f), Float2D(0f, 16f))

                recorder.calls.clear()
                mono.drawTextDecal(
                    Float2D(0f, 0f),
                    "A\tB",
                    Colors.WHITE,
                    Float2D(1f, 1f),
                    TAB_SIZE,
                    Int2D(320, 240),
                    Decal.Mode.NORMAL,
                    Decal.Structure.FAN,
                ) {}
                recorder.calls.map { it.position } shouldBe listOf(Float2D(0f, 0f), Float2D(80f, 0f))
            }
        }
    })

private class CoreDecalCall(
    val position: Float2D,
    val decal: Decal,
    val sourcePosition: Float2D,
    val sourceSize: Float2D,
    val scale: Float2D,
    val tint: Pixel,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    val viewport: Int2D,
)

private class CoreDecalRecorder(
    private val delegate: DrawPartialDecalService,
) : DrawPartialDecalService {
    val calls = mutableListOf<CoreDecalCall>()

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
        calls += CoreDecalCall(position, decal, sourcePosition, sourceSize, scale, tint, mode, structure, viewport)
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
