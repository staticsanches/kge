package dev.staticsanches.kge.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WindowConfigTest :
    FunSpec({
        test("defaults follow the olc Construct defaults") {
            val config = WindowConfig(screenWidth = 320, screenHeight = 240)

            config.pixelWidth shouldBe 1
            config.pixelHeight shouldBe 1
            config.title shouldBe ""
            config.resizable shouldBe true
            config.vsync shouldBe false
            config.fullScreen shouldBe false
            config.cohesion shouldBe false
        }

        test("non-positive screen sizes are rejected") {
            shouldThrow<IllegalArgumentException> { WindowConfig(screenWidth = 0, screenHeight = 240) }
            shouldThrow<IllegalArgumentException> { WindowConfig(screenWidth = -1, screenHeight = 240) }
            shouldThrow<IllegalArgumentException> { WindowConfig(screenWidth = 320, screenHeight = 0) }
            shouldThrow<IllegalArgumentException> { WindowConfig(screenWidth = 320, screenHeight = -1) }
        }

        test("non-positive pixel sizes are rejected") {
            shouldThrow<IllegalArgumentException> {
                WindowConfig(screenWidth = 320, screenHeight = 240, pixelWidth = 0)
            }
            shouldThrow<IllegalArgumentException> {
                WindowConfig(screenWidth = 320, screenHeight = 240, pixelWidth = -1)
            }
            shouldThrow<IllegalArgumentException> {
                WindowConfig(screenWidth = 320, screenHeight = 240, pixelHeight = 0)
            }
            shouldThrow<IllegalArgumentException> {
                WindowConfig(screenWidth = 320, screenHeight = 240, pixelHeight = -1)
            }
        }
    })
