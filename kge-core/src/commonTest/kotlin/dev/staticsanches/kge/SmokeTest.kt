package dev.staticsanches.kge

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmokeTest :
    FunSpec({
        test("kotest runs on this target") { (2 + 2).shouldBe(4) }
    })
