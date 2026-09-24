package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The service adopts a loaded font into a caller scope: the scope owns what it
 * adopts, its close releases every adopted font, and an override decides which.
 */
@OptIn(KGESensitiveAPI::class)
class TtfTextServiceTest :
    FunSpec({
        test("createResources makes the scope the owner, and closing it closes the font") {
            val font = Font.load(Roboto.variableFont)
            ResourceScope().use { scope ->
                TtfTextService.createResources(scope, font)

                scope.close()

                shouldThrow<IllegalStateException> { font.shape("A", 16) }
            }
        }

        test("one scope owns several adopted fonts and closes them all") {
            val first = Font.load(Roboto.variableFont)
            val second = Font.load(Roboto.variableFont)
            ResourceScope().use { scope ->
                TtfTextService.createResources(scope, first)
                TtfTextService.createResources(scope, second)

                scope.close()

                shouldThrow<IllegalStateException> { first.shape("A", 16) }
                shouldThrow<IllegalStateException> { second.shape("A", 16) }
            }
        }

        test("an overriding decorator adopts a different font than the caller passed") {
            val requested = Font.load(Roboto.variableFont)
            val adopted = Font.load(Roboto.variableFont)
            TtfTextService.override(
                object : TtfTextService {
                    override fun createResources(
                        scope: ResourceScope,
                        font: Font,
                    ) {
                        TtfTextService.original.createResources(scope, adopted)
                    }
                },
            )
            try {
                ResourceScope().use { scope ->
                    TtfTextService.createResources(scope, requested)
                    scope.close()

                    shouldThrow<IllegalStateException> { adopted.shape("A", 16) }
                    requested.shape("A", 16).glyphs.size shouldBe 1
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                TtfTextService.override(TtfTextService.original)
                requested.close()
                adopted.close()
            }
        }
    })
