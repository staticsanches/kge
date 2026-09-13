package dev.staticsanches.kge.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DriverServiceTest :
    FunSpec({
        test("create resolves the active implementation") {
            val driver = RecordingDriver()
            DriverService.override(
                object : DriverService {
                    override fun create(config: WindowConfig): Driver = driver
                },
            )

            val created = DriverService.create(WindowConfig(screenWidth = 320, screenHeight = 240))

            created shouldBe driver
        }
    })
