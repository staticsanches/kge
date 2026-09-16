package dev.staticsanches.kge.text

import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.golden.canvas
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val TAB_SIZE = 4

/**
 * The font lifecycle on the stateless service: [DrawStringService.createResources]
 * registers the sheet and its decal in the scope, which owns and closes them, and
 * a draw against a scope without the font fails fast.
 */
class DrawStringResourcesTest :
    FunSpec({
        test("createResources registers the font, which the scope closes") {
            val gl = installGl()
            ResourceScope().use { scope ->
                DrawStringService.createResources(scope)
                val sheet = fontSheet(scope)
                val decal = fontDecal(scope)
                gl.clear()

                scope.close()

                gl.calls.count { it.name == "deleteTexture" } shouldBe 1
                shouldThrow<IllegalStateException> { sheet.get(0, 0) }
                shouldThrow<IllegalStateException> { decal.update() }
            }
        }

        test("closing the scope twice releases the font once") {
            val gl = installGl()
            ResourceScope().use { scope ->
                DrawStringService.createResources(scope)
                gl.clear()

                scope.close()
                scope.close()

                gl.calls.count { it.name == "deleteTexture" } shouldBe 1
            }
        }

        test("a draw against a scope without the registered font fails fast") {
            ResourceScope().use { scope ->
                canvas(8, 8).use { target ->
                    shouldThrow<IllegalStateException> {
                        DrawStringService
                            .drawString(scope, target, 0, 0, "A", Colors.WHITE, 1, TAB_SIZE, Pixel.Mode.Normal)
                    }
                }
            }
        }
    })
