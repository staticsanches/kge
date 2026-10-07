package dev.staticsanches.kge.engine.console

import dev.staticsanches.kge.engine.input.InputState
import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.TextInputEvent
import dev.staticsanches.kge.engine.input.escape
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The console's command history and its two navigation directions. */
class ConsoleHistoryTest :
    FunSpec({
        test("UP loads the previously recorded command and is a no-op at the oldest") {
            val entry = TextEntry()
            val console = Console(entry, InputState(), { true }, {})
            console.show(KeyboardKey.escape)
            entry.enable("cmd1")
            console.handleEdit(TextInputEvent.Edit.ENTER)
            entry.enable("cmd2")
            console.handleEdit(TextInputEvent.Edit.ENTER)
            entry.text shouldBe ""

            console.handleEdit(TextInputEvent.Edit.UP)
            entry.text shouldBe "cmd2"
            entry.cursor shouldBe 4

            console.handleEdit(TextInputEvent.Edit.UP)
            entry.text shouldBe "cmd1"
            entry.cursor shouldBe 4

            console.handleEdit(TextInputEvent.Edit.UP)
            entry.text shouldBe "cmd1"
            entry.cursor shouldBe 4
        }

        test("DOWN moves toward the newest and one step past it clears the entry") {
            val entry = TextEntry()
            val console = Console(entry, InputState(), { true }, {})
            console.show(KeyboardKey.escape)
            entry.enable("cmd1")
            console.handleEdit(TextInputEvent.Edit.ENTER)
            entry.enable("cmd2")
            console.handleEdit(TextInputEvent.Edit.ENTER)

            console.handleEdit(TextInputEvent.Edit.UP)
            console.handleEdit(TextInputEvent.Edit.UP)
            entry.text shouldBe "cmd1"

            console.handleEdit(TextInputEvent.Edit.DOWN)
            entry.text shouldBe "cmd2"
            entry.cursor shouldBe 4

            console.handleEdit(TextInputEvent.Edit.DOWN)
            entry.text shouldBe ""
            entry.cursor shouldBe 0

            console.handleEdit(TextInputEvent.Edit.DOWN)
            entry.text shouldBe ""
            entry.cursor shouldBe 0
        }
    })
