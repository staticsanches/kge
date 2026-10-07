package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.engine.ScriptedEngine
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.testsupport.engine.RecordingDriver
import dev.staticsanches.kge.testsupport.engine.installDriver
import dev.staticsanches.kge.testsupport.engine.installGl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The raw text-input queue: platform events land in report order, one drain
 * takes them all, and the engine drains every frame so the queue cannot grow.
 */
class TextInputQueueTest :
    FunSpec({
        test("characters and edits drain in the order they were reported") {
            val raw = RawInput()
            raw.typedCharacter('a'.code)
            raw.pressedEdit(TextInputEvent.Edit.LEFT)
            raw.typedCharacter(0x1F600)
            raw.pressedEdit(TextInputEvent.Edit.ENTER)

            raw.drainTextInput() shouldBe
                listOf(
                    TextInputEvent.Character('a'.code),
                    TextInputEvent.Edit.LEFT,
                    TextInputEvent.Character(0x1F600),
                    TextInputEvent.Edit.ENTER,
                )
        }

        test("a drain empties the queue and a second drain is empty") {
            val raw = RawInput()
            raw.typedCharacter('x'.code)

            raw.drainTextInput().size shouldBe 1
            raw.drainTextInput() shouldBe emptyList()
        }

        test("the engine drains the queue even while the text entry is disabled") {
            val driver = RecordingDriver(scriptedFramebufferSize = Int2D(320, 240))
            installDriver(driver)
            installGl()
            var frames = 0
            val engine =
                ScriptedEngine(
                    onUpdate = { _, _ ->
                        if (++frames == 1) driver.input.typedCharacter('x'.code)
                        frames < 2
                    },
                )

            engine.start()

            driver.input.drainTextInput() shouldBe emptyList()
            frames shouldBe 2
        }
    })
