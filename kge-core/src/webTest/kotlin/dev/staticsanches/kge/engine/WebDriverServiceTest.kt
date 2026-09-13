package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import web.device.devicePixelRatio
import web.dom.document
import web.html.HTMLCanvasElement

class WebDriverServiceTest :
    FunSpec({
        test("the canvas-bound service reports the configured sizes and closes idempotently") {
            val config =
                WindowConfig(
                    screenWidth = 320,
                    screenHeight = 240,
                    pixelWidth = 2,
                    pixelHeight = 3,
                )
            val logical = Int2D(640, 720)
            val physical =
                Int2D(
                    (logical.x * devicePixelRatio).toInt(),
                    (logical.y * devicePixelRatio).toInt(),
                )
            val canvas = document.createElement("canvas") as HTMLCanvasElement

            val driver = WebDriverService(canvas).create(config)

            driver.windowSize() shouldBe logical
            driver.framebufferSize() shouldBe physical
            canvas.width shouldBe physical.x
            canvas.height shouldBe physical.y
            canvas.style.width shouldBe "${logical.x}px"
            canvas.style.height shouldBe "${logical.y}px"

            driver.makeCurrent()
            driver.present()
            driver.pollEvents()

            driver.close()
            driver.close()
        }

        test("the default service opens a driver on a browser canvas and removes it on close") {
            val childrenBefore = document.body.childElementCount

            val driver = DriverService.create(WindowConfig(screenWidth = 64, screenHeight = 48))

            driver.windowSize() shouldBe Int2D(64, 48)
            driver.makeCurrent()
            driver.present()
            driver.pollEvents()

            driver.close()
            driver.close()

            document.body.childElementCount shouldBe childrenBefore
        }
    })
