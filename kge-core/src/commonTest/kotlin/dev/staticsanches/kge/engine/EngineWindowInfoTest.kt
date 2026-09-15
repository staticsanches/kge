package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EngineWindowInfoTest :
    FunSpec({
        test("window reflects the config-derived screen and pixel sizes") {
            val config = WindowConfig(screenWidth = 640, screenHeight = 360, pixelWidth = 2, pixelHeight = 3)
            val engine = ScriptedEngine(config = config)

            engine.window.screenSize shouldBe Int2D(640, 360)
            engine.window.pixelSize shouldBe Int2D(2, 3)
            engine.window.invertedScreenSize shouldBe Float2D(1f / 640f, 1f / 360f)
            engine.window.config shouldBe config
        }

        test("the engine exposes the window, time and input roles") {
            installDriver(RecordingDriver(scriptedFramebufferSize = Int2D(320, 240)))
            installGl()
            val engine = ScriptedEngine(onUpdate = { _, _ -> false })
            val windowRole: HasWindow = engine
            val timeRole: HasTime = engine
            val inputRole: HasInput = engine

            engine.start()

            windowRole.window shouldBe engine.window
            timeRole.frame shouldBe engine.frame
            inputRole.input shouldBe engine.input
        }

        test("window sizes refresh from the driver each frame, observing a mid-run change") {
            val driver =
                RecordingDriver(
                    scriptedWindowSize = Int2D(320, 240),
                    scriptedFramebufferSize = Int2D(640, 480),
                )
            installDriver(driver)
            installGl()
            val seen = mutableListOf<Pair<Int2D, Int2D>>()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { e, _ ->
                        seen += e.window.windowSize to e.window.framebufferSize
                        if (++frames == 1) {
                            driver.scriptedWindowSize = Int2D(400, 300)
                            driver.scriptedFramebufferSize = Int2D(800, 600)
                        }
                        frames < 2
                    },
                )

            engine.start()

            seen shouldBe
                listOf(
                    Int2D(320, 240) to Int2D(640, 480),
                    Int2D(400, 300) to Int2D(800, 600),
                )
        }
    })
