package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.ButtonState
import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.escape
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The loop latches the driver's raw input once per frame, before the update
 * callback, so the callback observes the frame's [InputState].
 */
class EngineInputTest :
    FunSpec({
        test("the callback observes the frame's input snapshot") {
            val driver =
                RecordingDriver(
                    scriptedFramebufferSize = Int2D(320, 240),
                    scriptedWindowSize = Int2D(320, 240),
                )
            installDriver(driver)
            installGl()
            driver.input.setKeyDown(KeyboardKey.escape, true)
            driver.input.mousePosition = Int2D(160, 120)
            val seen = mutableListOf<ButtonState>()
            var observedMouse = Int2D.ZERO
            val engine =
                ScriptedEngine(
                    onUpdate = { engine, _ ->
                        seen += engine.input.key(KeyboardKey.escape)
                        observedMouse = engine.input.mousePosition
                        false
                    },
                )

            engine.start()

            seen.last().pressed shouldBe true
            seen.last().held shouldBe true
            observedMouse shouldBe Int2D(160, 120)
        }

        test("input refreshes between frames") {
            val driver =
                RecordingDriver(
                    scriptedFramebufferSize = Int2D(320, 240),
                    scriptedWindowSize = Int2D(320, 240),
                )
            installDriver(driver)
            installGl()
            val seen = mutableListOf<ButtonState>()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { engine, _ ->
                        seen += engine.input.key(KeyboardKey.escape)
                        if (++frames == 1) driver.input.setKeyDown(KeyboardKey.escape, false)
                        frames < 2
                    },
                )
            driver.input.setKeyDown(KeyboardKey.escape, true)

            engine.start()

            seen[0].pressed shouldBe true
            seen[1].released shouldBe true
        }
    })
