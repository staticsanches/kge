package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.KGEFont
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The loader seam: an override decides which family the caller receives, and the
 * module restores the engine default itself.
 */
@OptIn(KGESensitiveAPI::class)
class KGETtfFontServiceTest :
    FunSpec({
        test("an overriding decorator is the loader the returned family came from") {
            KGETtfFontService.override(
                object : KGETtfFontService by KGETtfFontService.original {
                    override suspend fun createResources(
                        scope: ResourceScope,
                        vararg bytes: ByteArray,
                    ): KGEFont.Family = KGETtfFontService.original.createResources(scope, robotoMonoBytes())
                },
            )
            try {
                ResourceScope().use { scope ->
                    val family = KGETtfFontService.createResources(scope, robotoFontBytes())

                    // the caller passed Roboto; the decorator loaded Roboto Mono
                    family.name shouldBe "Roboto Mono"
                    family.defaultFace.monospaced shouldBe true
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                KGETtfFontService.override(KGETtfFontService.original)
            }
        }
    })
