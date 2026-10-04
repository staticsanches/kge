package dev.staticsanches.kge.text.golden

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.golden.shouldMatchGolden
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asSequence
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.golden.canvas
import dev.staticsanches.kge.text.KGECoreFontFamily
import dev.staticsanches.kge.text.KGECoreFontService
import dev.staticsanches.kge.text.KGEFont
import dev.staticsanches.kge.text.fontPx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4

/**
 * The core faces through the public drawing surface against the authored
 * references, plus the two equivalences the copied references encode.
 */
@OptIn(KGESensitiveAPI::class)
class CoreFontGoldenTest :
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

        fun draw(
            font: KGEFont,
            target: Pixmap.Mutable,
            text: String,
            scale: Int,
        ) = font.drawText(target, 0, 0, text, Colors.WHITE, scale, TAB_SIZE, Pixel.Mode.Normal)

        test("mono at 8px scale 1 matches mono-8") {
            withFamily { scope, family ->
                canvas(16, 8).use { target ->
                    draw(family.mono(scope), target, "Hi", 1)
                    target.shouldMatchGolden("text/mono-8")
                }
            }
        }

        test("mono at 8px scale 2 matches mono-8-scale-2") {
            withFamily { scope, family ->
                canvas(32, 16).use { target ->
                    draw(family.mono(scope), target, "Hi", 2)
                    target.shouldMatchGolden("text/mono-8-scale-2")
                }
            }
        }

        test("prop at 8px scale 1 matches prop-8") {
            withFamily { scope, family ->
                canvas(11, 8).use { target ->
                    draw(family.prop(scope), target, "Hi", 1)
                    target.shouldMatchGolden("text/prop-8")
                }
            }
        }

        test("prop at 8px scale 2 matches prop-8-scale-2") {
            withFamily { scope, family ->
                canvas(22, 16).use { target ->
                    draw(family.prop(scope), target, "Hi", 2)
                    target.shouldMatchGolden("text/prop-8-scale-2")
                }
            }
        }

        test("mono at 16px scale 1 matches mono-16") {
            withFamily { scope, family ->
                canvas(32, 16).use { target ->
                    draw(family.mono(scope, 16), target, "Hi", 1)
                    target.shouldMatchGolden("text/mono-16")
                }
            }
        }

        test("prop at 16px scale 1 matches prop-16") {
            withFamily { scope, family ->
                canvas(22, 16).use { target ->
                    draw(family.prop(scope, 16), target, "Hi", 1)
                    target.shouldMatchGolden("text/prop-16")
                }
            }
        }

        test("mono at 16px scale 2 matches mono-16-scale-2") {
            withFamily { scope, family ->
                canvas(64, 32).use { target ->
                    draw(family.mono(scope, 16), target, "Hi", 2)
                    target.shouldMatchGolden("text/mono-16-scale-2")
                }
            }
        }

        test("the whole 96-cell payload drawn through the mono face matches payload-mono-8") {
            withFamily { scope, family ->
                canvas(128, 48).use { target ->
                    val payload = (32..127).map { it.toChar() }.chunked(16).joinToString("\n") { it.joinToString("") }
                    draw(family.mono(scope), target, payload, 1)
                    target.shouldMatchGolden("text/payload-mono-8")
                }
            }
        }

        test("the base size at scale 1 paints the same pixels as 8px at scale 2") {
            withFamily { scope, family ->
                canvas(32, 16).use { baseScaled ->
                    canvas(32, 16).use { callScaled ->
                        draw(family.mono(scope, 16), baseScaled, "Hi", 1)
                        draw(family.mono(scope, 8), callScaled, "Hi", 2)
                        assertSamePixels(baseScaled, callScaled)
                    }
                }
                canvas(22, 16).use { baseScaled ->
                    canvas(22, 16).use { callScaled ->
                        draw(family.prop(scope, 16), baseScaled, "Hi", 1)
                        draw(family.prop(scope, 8), callScaled, "Hi", 2)
                        assertSamePixels(baseScaled, callScaled)
                    }
                }
            }
        }
    })

private fun assertSamePixels(
    actual: Pixmap,
    expected: Pixmap,
) {
    actual.width shouldBe expected.width
    actual.height shouldBe expected.height
    actual.asSequence().toList() shouldBe expected.asSequence().toList()
}
