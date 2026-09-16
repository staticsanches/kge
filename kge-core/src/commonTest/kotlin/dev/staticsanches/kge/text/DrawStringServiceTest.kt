package dev.staticsanches.kge.text

import dev.staticsanches.kge.engine.installGl
import dev.staticsanches.kge.golden.shouldMatchGolden
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The default [DrawStringService] builds olc v2.30's bitmap font sheet into the
 * scope: a 128x48 surface of 8x8 cells, 16 per row, painted from character 32.
 */
class DrawStringServiceTest :
    FunSpec({
        test("createResources builds the 128x48 sheet and its decal") {
            installGl()
            ResourceScope().use { scope ->
                DrawStringService.createResources(scope)
                fontSheet(scope).width shouldBe 128
                fontSheet(scope).height shouldBe 48
            }
        }

        test("the painted sheet matches the olc-derived golden") {
            installGl()
            ResourceScope().use { scope ->
                DrawStringService.createResources(scope)
                fontSheet(scope).shouldMatchGolden("text/sheet")
            }
        }
    })
