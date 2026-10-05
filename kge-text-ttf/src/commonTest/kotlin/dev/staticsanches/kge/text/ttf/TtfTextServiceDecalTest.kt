@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.testsupport.engine.installGl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The decal text draw: one instance per ink glyph, anchored by the hand-derived
 * destination and carrying the caller's mode, structure and viewport unchanged.
 */
class TtfTextServiceDecalTest :
    FunSpec({
        test("the collected instance matches the hand-derived worked example") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    val collected = drawDecal(font, "A")

                    collected.size shouldBe 1
                    // round D: "A" at 16 px is 11x12 at bearing (0, -12); round C: the
                    // 16 px ascender is 14.84375, so the line-relative pen is (0, 14.84375).
                    // destination = (2, 3) + (0, 14.84375) + (0, -12) = (2, 5.84375)
                    assertSameGeometry(
                        collected.single(),
                        expectedInstance(font, "A", Float2D(2f, 5.84375f)),
                    )
                }
            }
        }

        test("a non-uniform scale multiplies the pen and the bearing per axis") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    val scale = Float2D(2f, 3f)

                    // round D: "A" 11x12 bearing (0, -12); round C: the 16 px ascender is
                    // 14.84375 and pen.y is 14.84375, so y = 3 + (14.84375 - 12) * 3 = 11.53125
                    assertSameGeometry(
                        drawDecal(font, "A", scale = scale).single(),
                        expectedInstance(font, "A", Float2D(2f, 11.53125f), scale = scale),
                    )

                    // round D: "1" 5x12 bearing (1, -12): bearing.x * 2 = 2, not * 3
                    assertSameGeometry(
                        drawDecal(font, "1", scale = scale).single(),
                        expectedInstance(font, "1", Float2D(4f, 11.53125f), scale = scale),
                    )

                    // round C: "A" advances 10.4375, so the second pen.x is 10.4375 and
                    // 10.4375 * 2 = 20.875 (bearing.x is 0)
                    assertSameGeometry(
                        drawDecal(font, "AA", scale = scale).last(),
                        expectedInstance(font, "AA", Float2D(22.875f, 11.53125f), index = 1, scale = scale),
                    )
                }
            }
        }

        test("a blank glyph is skipped and its advance still moves the pen") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    val collected = drawDecal(font, "A 1")

                    // three shaped glyphs, two with ink
                    collected.size shouldBe 2
                    // round C: "A" advances 10.4375 and the isolated space 3.96875, so the
                    // "1" pen is 14.40625; with bearing (1, -12):
                    // destination = (2 + 14.40625 + 1, 3 + 14.84375 - 12) = (17.40625, 5.84375)
                    assertSameGeometry(
                        collected.last(),
                        expectedInstance(font, "1", Float2D(17.40625f, 5.84375f)),
                    )
                    // the first instance is the leading "A", not the skipped space
                    assertSameGeometry(
                        collected.first(),
                        expectedInstance(font, "A", Float2D(2f, 5.84375f)),
                    )
                }
            }
        }

        test("the decal mode, structure and viewport reach the instance unchanged") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    val viewport = Int2D(64, 48)
                    val collected =
                        drawDecal(
                            font,
                            "A",
                            screenSize = viewport,
                            decalMode = Decal.Mode.ADDITIVE,
                            decalStructure = Decal.Structure.STRIP,
                        ).single()

                    collected.mode shouldBe Decal.Mode.ADDITIVE
                    collected.structure shouldBe Decal.Structure.STRIP
                    assertSameGeometry(
                        collected,
                        expectedInstance(
                            font,
                            "A",
                            Float2D(2f, 5.84375f),
                            screenSize = viewport,
                            decalMode = Decal.Mode.ADDITIVE,
                            decalStructure = Decal.Structure.STRIP,
                        ),
                    )
                    // the viewport is not inert: the same anchor quantises differently at 30x24
                    val defaultViewport = expectedInstance(font, "A", Float2D(2f, 5.84375f))
                    (collected.vertices.x(0) != defaultViewport.vertices.x(0)) shouldBe true
                }
            }
        }

        test("empty text collects nothing and a multi-glyph draw collects in walk order") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    drawDecal(font, "").size shouldBe 0

                    val collected = drawDecal(font, "AV")

                    collected.size shouldBe 2
                    // round D: "A" 11x12, "V" 10x12, both bearing (0, -12); round C: the
                    // kerned "AV" pins the V pen at 9.765625, so its anchor is 2 + 9.765625
                    assertSameGeometry(
                        collected.first(),
                        expectedInstance(font, "A", Float2D(2f, 5.84375f)),
                    )
                    assertSameGeometry(
                        collected.last(),
                        expectedInstance(font, "AV", Float2D(11.765625f, 5.84375f), index = 1),
                    )
                }
            }
        }

        test("the tint reaches the instance's vertices unchanged") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    val tint = Pixel.rgba(0x3366CCFFu)

                    val collected = drawDecal(font, "A", color = tint).single()

                    // read the tint off the instance itself, not just "an instance exists"
                    collected.vertices.tint(0) shouldBe tint
                    collected.vertices.tint(3) shouldBe tint
                    assertSameGeometry(
                        collected,
                        expectedInstance(font, "A", Float2D(2f, 5.84375f), color = tint),
                    )
                }
            }
        }

        test("the tab size moves the following glyph to its hand-derived stop") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    // round C: "A" advances 10.4375 and the isolated space 3.96875; E1's tab
                    // stop is pen = (floor(pen / step) + 1) * step with step = tab * 3.96875.
                    // tab 4: step 15.875, floor(10.4375 / 15.875) = 0 -> pen 15.875
                    // -> destination (2 + 15.875 + 1, 3 + 14.84375 - 12) = (18.875, 5.84375)
                    val four = drawDecal(font, "A\t1", tabSizeInSpaces = 4).last()
                    assertSameGeometry(four, expectedInstance(font, "1", Float2D(18.875f, 5.84375f)))

                    // tab 1: step 3.96875, floor(10.4375 / 3.96875) = 2 -> pen 3 * 3.96875 =
                    // 11.90625 -> destination (2 + 11.90625 + 1, 5.84375)
                    val oneSpace = drawDecal(font, "A\t1", tabSizeInSpaces = 1).last()
                    assertSameGeometry(oneSpace, expectedInstance(font, "1", Float2D(14.90625f, 5.84375f)))

                    (four.vertices.x(0) != oneSpace.vertices.x(0)) shouldBe true
                }
            }
        }

        test("a different sizePx draws from that size's atlas at that size's advance") {
            withRecordingGl {
                Font.load(Roboto.romanFont).use { font ->
                    val viewport = Int2D(64, 48)

                    // round D: "A" at 32 px is 21x23 at bearing (0, -23); round C scales the
                    // 16 px ascender 14.84375 by two, so the anchor is (2, 3 + 29.6875 - 23)
                    assertSameGeometry(
                        drawDecal(font, "A", sizePx = 32, screenSize = viewport).single(),
                        expectedInstance(font, "A", Float2D(2f, 9.6875f), sizePx = 32, screenSize = viewport),
                    )

                    // the 32 px advance is 2 * 10.4375 = 20.875, so the second "A" anchors at 22.875
                    assertSameGeometry(
                        drawDecal(font, "AA", sizePx = 32, screenSize = viewport).last(),
                        expectedInstance(
                            font,
                            "AA",
                            Float2D(22.875f, 9.6875f),
                            index = 1,
                            sizePx = 32,
                            screenSize = viewport,
                        ),
                    )
                }
            }
        }

        test("updating a collected instance's decal re-specifies the chart as RGBA") {
            val gl = installGl()
            try {
                Font.load(Roboto.romanFont).use { font ->
                    val decal = drawDecal(font, "A").single().decal
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
            } finally {
                GLService.override(GLService.original)
            }
        }
    })

/** Installs the recording GL service and restores the engine default afterwards. */
private inline fun withRecordingGl(block: () -> Unit) {
    installGl()
    try {
        block()
    } finally {
        GLService.override(GLService.original)
    }
}

/** Draws [text] at 16 px with the service and returns the collected instances in walk order. */
private fun drawDecal(
    font: Font,
    text: String,
    position: Float2D = Float2D(2f, 3f),
    sizePx: Int = 16,
    color: Pixel = Colors.WHITE,
    scale: Float2D = Float2D(1f, 1f),
    tabSizeInSpaces: Int = 4,
    screenSize: Int2D = Int2D(30, 24),
    decalMode: Decal.Mode = Decal.Mode.NORMAL,
    decalStructure: Decal.Structure = Decal.Structure.FAN,
): List<DecalInstance> {
    val collected = mutableListOf<DecalInstance>()
    TtfTextService.drawStringDecal(
        font,
        position,
        text,
        sizePx,
        color,
        scale,
        tabSizeInSpaces,
        screenSize,
        decalMode,
        decalStructure,
        collected::add,
    )
    return collected
}
