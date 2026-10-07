package dev.staticsanches.kge.engine.console

import dev.staticsanches.kge.engine.input.InputState
import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.escape
import dev.staticsanches.kge.engine.input.f1
import dev.staticsanches.kge.math.vector.Int2D
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The console transcript: what `write` becomes on the next frame, the ASCII
 * filter, the wrap and scroll rules, the grid reset, `clear` and the backlog.
 */
class ConsoleBufferTest :
    FunSpec({
        test("write text becomes transcript lines on the next console frame") {
            val console = console()

            console.show(KeyboardKey.escape)
            console.write("hello")
            console.update(Int2D(160, 128))

            console.bufferSnapshot().first[0] shouldBe "hello"
        }

        test("only ASCII 32..126 enters the transcript") {
            val console = console()

            console.show(KeyboardKey.escape)
            console.write("a\tb\u0001c")
            console.update(Int2D(160, 128))

            console.bufferSnapshot().first[0] shouldBe "abc"
        }

        test("a newline starts a new line") {
            val console = console()

            console.show(KeyboardKey.escape)
            console.write("a\nb")
            console.update(Int2D(160, 128))

            console.bufferSnapshot().first.take(2) shouldBe listOf("a", "b")
        }

        test("a line that fills the width wraps and keeps the overflow character") {
            val console = console()
            console.show(KeyboardKey.escape)
            console.update(Int2D(160, 128))
            val width = console.bufferSnapshot().second.x
            val text = ('a'..'z').take(width + 1).joinToString("")

            console.write(text)
            console.update(Int2D(160, 128))

            val lines = console.bufferSnapshot().first
            lines[0] shouldBe text.take(width)
            lines[1] shouldBe text.substring(width)
        }

        test("passing the last line scrolls every line up and clears the last") {
            val console = console()

            console.show(KeyboardKey.escape)
            console.write("1\n2\n3\n4\n")
            console.update(Int2D(160, 128))

            console.bufferSnapshot().first shouldBe listOf("2", "3", "4", "")
        }

        test("a grid-height change clears the transcript and resets the cursor") {
            val console = console()
            console.show(KeyboardKey.escape)
            console.write("ab")
            console.update(Int2D(160, 240))
            val width = console.bufferSnapshot().second.x
            console.bufferSnapshot().first[0] shouldBe "ab"

            console.update(Int2D(160, 256))

            console.bufferSnapshot().first.all { it.isEmpty() } shouldBe true

            console.write("x".repeat(width))
            console.update(Int2D(160, 256))

            val lines = console.bufferSnapshot().first
            lines[0] shouldBe "x".repeat(width)
            lines[1] shouldBe ""
        }

        test("clear empties the transcript") {
            val console = console()
            console.show(KeyboardKey.escape)
            console.write("hi")
            console.update(Int2D(160, 128))
            console.bufferSnapshot().first[0] shouldBe "hi"

            console.clear()

            console.bufferSnapshot().first.joinToString("") shouldBe ""
            console.update(Int2D(160, 128))
            console.bufferSnapshot().first.all { it.isEmpty() } shouldBe true
        }

        test("show on an already showing console changes nothing") {
            val entry = TextEntry()
            val console = Console(entry, InputState(), { false }, {})
            console.show(KeyboardKey.escape, suspendTime = true)
            entry.enable("abc")
            console.write("kept")

            console.show(KeyboardKey.f1, suspendTime = false)
            console.update(Int2D(160, 128))

            entry.text shouldBe "abc"
            entry.isEnabled shouldBe true
            console.isShowing shouldBe true
            console.isTimeSuspended shouldBe true
            console.bufferSnapshot().first[0] shouldBe "kept"
        }

        test("the backlog is bounded while hidden and keeps the newest output") {
            val console = console()

            console.write("HEAD")
            console.write("A".repeat(4096))
            console.write("TAIL")
            console.show(KeyboardKey.escape)
            console.update(Int2D(1600, 1024))

            val transcript = console.bufferSnapshot().first.joinToString("")
            transcript.contains("HEAD") shouldBe false
            transcript.endsWith("TAIL") shouldBe true
        }
    })

private fun console(): Console = Console(TextEntry(), InputState(), { false }, {})
