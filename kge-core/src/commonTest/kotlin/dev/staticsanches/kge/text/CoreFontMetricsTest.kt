package dev.staticsanches.kge.text

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.testsupport.engine.installGl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private const val TAB_SIZE = 4

/**
 * The core font metrics: the widest line by the tallest line, mono 8px cells
 * versus the proportional per-character advances, and the configured base size.
 */
@OptIn(KGESensitiveAPI::class)
class CoreFontMetricsTest :
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

        test("mono size is the widest line by the tallest line, in 8px cells") {
            withFamily { scope, family ->
                family.mono(scope).measureText("AB", TAB_SIZE) shouldBe Int2D(16, 8)
            }
        }

        test("mono size resets x and grows y on a newline") {
            withFamily { scope, family ->
                family.mono(scope).measureText("A\nB", TAB_SIZE) shouldBe Int2D(8, 16)
            }
        }

        test("mono size treats a carriage return as an ordinary cell; only a newline breaks a line") {
            withFamily { scope, family ->
                val mono = family.mono(scope)
                mono.measureText("A\rB", TAB_SIZE) shouldBe Int2D(24, 8)
                mono.measureText("A\rB", TAB_SIZE) shouldBe mono.measureText("A B", TAB_SIZE)
                mono.measureText("A\r\nB", TAB_SIZE) shouldBe Int2D(16, 16)
                mono.measureText("A\rB", TAB_SIZE) shouldNotBe mono.measureText("A\nB", TAB_SIZE)
                mono.measureText("A\r\nB", TAB_SIZE) shouldNotBe mono.measureText("A\nB", TAB_SIZE)
            }
        }

        test("mono size advances x by tabSizeInSpaces cells on a tab") {
            withFamily { scope, family ->
                family.mono(scope).measureText("A\tB", TAB_SIZE) shouldBe Int2D(48, 8)
            }
        }

        test("mono size rejects a non-positive tab size") {
            withFamily { scope, family ->
                val mono = family.mono(scope)
                shouldThrow<IllegalStateException> { mono.measureText("A", 0) }
                shouldThrow<IllegalStateException> { mono.measureText("A", -1) }
            }
        }

        test("prop size sums the per-character advances of the spacing table") {
            withFamily { scope, family ->
                family.prop(scope).measureText("Ai", TAB_SIZE) shouldBe Int2D(11, 8)
            }
        }

        test("prop size resets x, grows y on a newline and scales only y by 8 at the end") {
            withFamily { scope, family ->
                family.prop(scope).measureText("Ai\nB", TAB_SIZE) shouldBe Int2D(11, 16)
            }
        }

        test("prop size advances x by tabSizeInSpaces * 8 on a tab") {
            withFamily { scope, family ->
                family.prop(scope).measureText("A\tB", TAB_SIZE) shouldBe Int2D(48, 8)
            }
        }

        test("prop size rejects a non-positive tab size") {
            withFamily { scope, family ->
                val prop = family.prop(scope)
                shouldThrow<IllegalStateException> { prop.measureText("A", 0) }
                shouldThrow<IllegalStateException> { prop.measureText("A", -1) }
            }
        }

        test("an empty string measures (0, 0) for every configured size") {
            withFamily { scope, family ->
                for (px in listOf(8, 16)) {
                    family.mono(scope, px).measureText("", TAB_SIZE) shouldBe Int2D(0, 0)
                    family.prop(scope, px).measureText("", TAB_SIZE) shouldBe Int2D(0, 0)
                }
            }
        }

        test("the base size multiplies the measured width and height") {
            withFamily { scope, family ->
                family.mono(scope, 8).measureText("AB", TAB_SIZE) shouldBe Int2D(16, 8)
                family.mono(scope, 16).measureText("AB", TAB_SIZE) shouldBe Int2D(32, 16)
                family.prop(scope, 8).measureText("Hi", TAB_SIZE) shouldBe Int2D(11, 8)
                family.prop(scope, 16).measureText("Hi", TAB_SIZE) shouldBe Int2D(22, 16)
            }
        }
    })
