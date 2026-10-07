package dev.staticsanches.kge.engine.console

import dev.staticsanches.kge.engine.input.TextInputEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The editable line: what `enable`/`disable` keep, and the five edit
 * operations over whole code points.
 */
class TextEntryTest :
    FunSpec({
        test("enable stores the text with the cursor at its end, and no argument starts empty") {
            val entry = TextEntry()

            entry.enable("abc")
            entry.text shouldBe "abc"
            entry.cursor shouldBe 3
            entry.isEnabled shouldBe true

            entry.enable()
            entry.text shouldBe ""
            entry.cursor shouldBe 0
        }

        test("disable clears only the flag; the text and cursor survive") {
            val entry = TextEntry()
            entry.enable("abc")
            entry.apply(TextInputEvent.Edit.LEFT)

            entry.disable()

            entry.isEnabled shouldBe false
            entry.text shouldBe "abc"
            entry.cursor shouldBe 2
        }

        test("a character inserts at the cursor and advances it") {
            val entry = TextEntry()
            entry.enable("ab")
            entry.apply(TextInputEvent.Edit.LEFT)

            entry.apply(TextInputEvent.Character('X'.code))

            entry.text shouldBe "aXb"
            entry.cursor shouldBe 2
        }

        test("LEFT and RIGHT move one code point and stop at the bounds") {
            val entry = TextEntry()
            entry.enable("ab")

            entry.apply(TextInputEvent.Edit.RIGHT)
            entry.cursor shouldBe 2

            entry.apply(TextInputEvent.Edit.LEFT)
            entry.apply(TextInputEvent.Edit.LEFT)
            entry.apply(TextInputEvent.Edit.LEFT)
            entry.cursor shouldBe 0
        }

        test("BACKSPACE erases the character before the cursor and does nothing at 0") {
            val entry = TextEntry()
            entry.enable("ab")

            entry.apply(TextInputEvent.Edit.BACKSPACE)
            entry.text shouldBe "a"
            entry.cursor shouldBe 1

            entry.apply(TextInputEvent.Edit.LEFT)
            entry.apply(TextInputEvent.Edit.BACKSPACE)
            entry.text shouldBe "a"
            entry.cursor shouldBe 0
        }

        test("DELETE erases at the cursor and does nothing at the end") {
            val entry = TextEntry()
            entry.enable("ab")
            entry.apply(TextInputEvent.Edit.LEFT)

            entry.apply(TextInputEvent.Edit.DELETE)
            entry.text shouldBe "a"
            entry.cursor shouldBe 1

            entry.apply(TextInputEvent.Edit.DELETE)
            entry.text shouldBe "a"
            entry.cursor shouldBe 1
        }

        test("a character beyond the BMP is inserted whole and LEFT steps over it") {
            val entry = TextEntry()
            entry.enable()

            entry.apply(TextInputEvent.Character(0x1F600))

            entry.text shouldBe "\uD83D\uDE00"
            entry.cursor shouldBe 2

            entry.apply(TextInputEvent.Edit.LEFT)
            entry.cursor shouldBe 0

            entry.apply(TextInputEvent.Edit.RIGHT)
            entry.cursor shouldBe 2

            entry.apply(TextInputEvent.Edit.BACKSPACE)
            entry.text shouldBe ""
            entry.cursor shouldBe 0
        }
    })
