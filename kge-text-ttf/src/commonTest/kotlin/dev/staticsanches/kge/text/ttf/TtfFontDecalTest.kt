package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.gl.RecordingGLService
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private const val TAB_SIZE = 4
private const val SIZE_PX = 16

/**
 * The configured TrueType font's decal draw: one instance per ink glyph in walk
 * order, at the hand-derived destination, carrying every caller parameter.
 */
@OptIn(KGESensitiveAPI::class)
class TtfFontDecalTest :
    FunSpec({
        suspend fun configured(scope: ResourceScope): KGEFont =
            KGETtfFontService
                .createResources(scope, robotoFontBytes(), robotoItalicBytes())
                .defaultFace
                .font(scope, SIZE_PX.fontPx)

        test("the collected instance matches the hand-derived worked example") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val collected = font.collectDecal("A")

                    collected.size shouldBe 1
                    // "A" at 16 px is 11x12 at bearing (0, -12) and the ascender is
                    // 14.84375: (2, 3) + (0, 14.84375) + (0, -12) = (2, 5.84375)
                    assertSameQuad(
                        collected.single(),
                        expectedInstance("A", Float2D(2f, 5.84375f)),
                    )
                    // the shared configuration keeps one carrier across the font's draws
                    collected.single().decal shouldBeSameInstanceAs font.collectDecal("A").single().decal
                }
            }
        }

        test("a blank glyph is skipped while its advance still moves the pen") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val collected = font.collectDecal("A 1")

                    // three shaped glyphs, two with ink
                    collected.size shouldBe 2
                    assertSameQuad(
                        collected.first(),
                        expectedInstance("A", Float2D(2f, 5.84375f)),
                    )
                    // "A" advances 10.4375 and the isolated space 3.96875, so the "1"
                    // pen is 14.40625; with bearing (1, -12) it anchors at (17.40625, 5.84375)
                    assertSameQuad(
                        collected.last(),
                        expectedInstance("1", Float2D(17.40625f, 5.84375f)),
                    )
                }
            }
        }

        test("a non-uniform scale multiplies the pen and the bearing per axis") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val scale = Float2D(2f, 3f)

                    // "A" 11x12 bearing (0, -12): y = 3 + (14.84375 - 12) * 3 = 11.53125
                    assertSameQuad(
                        font.collectDecal("A", scale = scale).single(),
                        expectedInstance("A", Float2D(2f, 11.53125f), scale = scale),
                    )
                    val one = font.collectDecal("1", scale = scale).single()
                    // "1" 5x12 bearing (1, -12): bearing.x * 2 = 2, not * 3
                    assertSameQuad(
                        one,
                        expectedInstance("1", Float2D(4f, 11.53125f), scale = scale),
                    )
                    // "A" advances 10.4375, so the second pen.x is 20.875 (bearing.x is 0)
                    assertSameQuad(
                        font.collectDecal("AA", scale = scale).last(),
                        expectedInstance("AA", Float2D(22.875f, 11.53125f), index = 1, scale = scale),
                    )

                    // moving one component alone moves only its own axis
                    val unitX = font.collectDecal("1", scale = Float2D(1f, 3f)).single()
                    (one.vertices.x(0) != unitX.vertices.x(0)) shouldBe true
                    one.vertices.y(0) shouldBe unitX.vertices.y(0)
                    val unitY = font.collectDecal("1", scale = Float2D(2f, 1f)).single()
                    one.vertices.x(0) shouldBe unitY.vertices.x(0)
                    (one.vertices.y(0) != unitY.vertices.y(0)) shouldBe true
                }
            }
        }

        test("the decal mode, structure, viewport and tint reach the instance unchanged") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val viewport = Int2D(64, 48)
                    val tint = Pixel.rgba(0x3366CCFFu)
                    val collected =
                        font
                            .collectDecal(
                                "A",
                                color = tint,
                                screenSize = viewport,
                                decalMode = Decal.Mode.ADDITIVE,
                                decalStructure = Decal.Structure.STRIP,
                            ).single()

                    collected.mode shouldBe Decal.Mode.ADDITIVE
                    collected.structure shouldBe Decal.Structure.STRIP
                    collected.vertices.tint(0) shouldBe tint
                    collected.vertices.tint(3) shouldBe tint
                    assertSameQuad(
                        collected,
                        expectedInstance(
                            "A",
                            Float2D(2f, 5.84375f),
                            color = tint,
                            screenSize = viewport,
                            decalMode = Decal.Mode.ADDITIVE,
                            decalStructure = Decal.Structure.STRIP,
                        ),
                    )
                    // the viewport is not inert: the same anchor quantises differently at 30x24
                    val defaultViewport = expectedInstance("A", Float2D(2f, 5.84375f), color = tint)
                    (collected.vertices.x(0) != defaultViewport.vertices.x(0)) shouldBe true
                }
            }
        }

        test("empty text collects nothing and a multi-glyph draw collects in walk order") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    var calls = 0
                    font.drawTextDecal(
                        Float2D(2f, 3f),
                        "",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(30, 24),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                    ) { calls++ }
                    calls shouldBe 0
                    font.collectDecal("").size shouldBe 0
                    font.collectDecal("\n\t ").size shouldBe 0

                    val collected = font.collectDecal("AV")
                    collected.size shouldBe 2
                    assertSameQuad(
                        collected.first(),
                        expectedInstance("A", Float2D(2f, 5.84375f)),
                    )
                    // the kerned "AV" pins the V pen at 9.765625
                    assertSameQuad(
                        collected.last(),
                        expectedInstance("AV", Float2D(11.765625f, 5.84375f), index = 1),
                    )
                }
            }
        }

        test("the tab size moves the following glyph to its hand-derived stop") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    // "A" advances 10.4375 and the isolated space 3.96875; the stop is
                    // (floor(pen / step) + 1) * step with step = tab * 3.96875.
                    // tab 4: step 15.875, floor(10.4375 / 15.875) = 0, so pen 15.875
                    val four = font.collectDecal("A\t1", tabSizeInSpaces = 4)
                    four.size shouldBe 2 // the tab itself collects nothing
                    assertSameQuad(four.last(), expectedInstance("1", Float2D(18.875f, 5.84375f)))

                    // tab 1: step 3.96875, floor(10.4375 / 3.96875) = 2, so pen 11.90625
                    val oneSpace = font.collectDecal("A\t1", tabSizeInSpaces = 1).last()
                    assertSameQuad(oneSpace, expectedInstance("1", Float2D(14.90625f, 5.84375f)))

                    (four.last().vertices.x(0) != oneSpace.vertices.x(0)) shouldBe true
                }
            }
        }

        test("zero, negative, NaN and infinite scale components propagate") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    // zero: the glyph offset vanishes and the quad still queues at position
                    val zero = font.collectDecal("A", scale = Float2D(0f, 0f)).single()
                    assertSameQuad(
                        zero,
                        expectedInstance("A", Float2D(2f, 3f), scale = Float2D(0f, 0f)),
                    )

                    // negative: the bearing mirrors on x and y keeps the *3 anchor
                    val negative = font.collectDecal("1", scale = Float2D(-2f, 3f)).single()
                    assertSameQuad(
                        negative,
                        expectedInstance("1", Float2D(0f, 11.53125f), scale = Float2D(-2f, 3f)),
                    )

                    // NaN: the instance still queues and y keeps its finite anchor
                    val nan = font.collectDecal("A", scale = Float2D(Float.NaN, 1f)).single()
                    nan.vertices.x(0).isNaN() shouldBe true
                    val unit = font.collectDecal("A").single()
                    nan.vertices.y(0) shouldBe unit.vertices.y(0)

                    // infinite: a placed bearing carries the infinity through as-is
                    val infinite = font.collectDecal("A1", scale = Float2D(Float.POSITIVE_INFINITY, 1f))
                    infinite.size shouldBe 2
                    infinite.last().vertices.x(0) shouldBe Float.POSITIVE_INFINITY
                }
            }
        }

        test("drawing after the font is closed fails fast and queues nothing") {
            ResourceScope().use { scope ->
                val font = configured(scope)
                font.close()

                val collected = mutableListOf<DecalInstance>()
                shouldThrow<IllegalStateException> {
                    font.drawTextDecal(
                        Float2D(2f, 3f),
                        "A",
                        Colors.WHITE,
                        Float2D(1f, 1f),
                        TAB_SIZE,
                        Int2D(30, 24),
                        Decal.Mode.NORMAL,
                        Decal.Structure.FAN,
                        collected::add,
                    )
                }
                collected shouldBe emptyList()
            }
        }

        test("two leases at different sizes draw from their own atlas and advance") {
            withRecordingGl {
                ResourceScope().use { scope ->
                    val face =
                        KGETtfFontService
                            .createResources(scope, robotoFontBytes(), robotoItalicBytes())
                            .defaultFace
                    val small = face.font(scope, SIZE_PX.fontPx)
                    val large = face.font(scope, 32.fontPx)
                    val viewport = Int2D(64, 48)

                    val smallA = small.collectDecal("A", screenSize = viewport).single()
                    assertSameQuad(
                        smallA,
                        expectedInstance("A", Float2D(2f, 5.84375f), screenSize = viewport),
                    )

                    // "A" at 32 px is 21x23 at bearing (0, -23), so the anchor is (2, 3 + 29.6875 - 23)
                    val largeA = large.collectDecal("A", screenSize = viewport).single()
                    assertSameQuad(
                        largeA,
                        expectedInstance("A", Float2D(2f, 9.6875f), sizePx = 32, screenSize = viewport),
                    )
                    // the 32 px advance is 2 * 10.4375 = 20.875, so the second "A" anchors at 22.875
                    assertSameQuad(
                        large.collectDecal("AA", screenSize = viewport).last(),
                        expectedInstance(
                            "AA",
                            Float2D(22.875f, 9.6875f),
                            index = 1,
                            sizePx = 32,
                            screenSize = viewport,
                        ),
                    )
                    (smallA.decal === largeA.decal) shouldBe false
                }
            }
        }

        test("updating a collected instance's decal re-specifies the chart as RGBA") {
            withRecordingGl { gl ->
                ResourceScope().use { scope ->
                    val font = configured(scope)
                    val decal = font.collectDecal("A").single().decal
                    // the carrier's own creation: one channel under the swizzle where the platform stores one
                    gl.calls.single { it.name == "texImage2D" }.arguments[2] shouldBe
                        if (coverageTextureIsSingleChannel) GL.R8 else GL.RGBA
                    gl.clear()

                    // Decal.update() re-specifies the whole chart as RGBA: the swizzle stays
                    // armed over the RGBA store, so every glyph box would sample opaque.
                    decal.update()

                    gl.calls.map { it.name } shouldBe listOf("bindTexture", "texImage2D")
                    val reSpecified = gl.calls.single { it.name == "texImage2D" }
                    reSpecified.arguments.slice(2..4) shouldBe listOf(GL.RGBA, 512, 512)
                }
            }
        }
    })

/** Installs the recording GL service and restores the engine default afterwards. */
@OptIn(KGESensitiveAPI::class)
private inline fun withRecordingGl(block: (RecordingGLService) -> Unit) {
    val gl = installGl()
    try {
        block(gl)
    } finally {
        GLService.override(GLService.original)
    }
}

/** Draws [text] at 16 px through the configured font and returns the instances in walk order. */
private fun KGEFont.collectDecal(
    text: String,
    position: Float2D = Float2D(2f, 3f),
    color: Pixel = Colors.WHITE,
    scale: Float2D = Float2D(1f, 1f),
    tabSizeInSpaces: Int = TAB_SIZE,
    screenSize: Int2D = Int2D(30, 24),
    decalMode: Decal.Mode = Decal.Mode.NORMAL,
    decalStructure: Decal.Structure = Decal.Structure.FAN,
): List<DecalInstance> {
    val collected = mutableListOf<DecalInstance>()
    drawTextDecal(position, text, color, scale, tabSizeInSpaces, screenSize, decalMode, decalStructure, collected::add)
    return collected
}

/**
 * The drawn quad against the hand-built one: the caller's parameters and the
 * quantised destination; the carrier owns the decal and its UVs.
 */
private fun assertSameQuad(
    actual: DecalInstance,
    expected: DecalInstance,
) {
    actual.mode shouldBe expected.mode
    actual.structure shouldBe expected.structure
    actual.vertexCount shouldBe expected.vertexCount
    for (index in 0 until expected.vertexCount) {
        actual.vertices.x(index) shouldBe expected.vertices.x(index)
        actual.vertices.y(index) shouldBe expected.vertices.y(index)
        actual.vertices.tint(index) shouldBe expected.vertices.tint(index)
    }
}
