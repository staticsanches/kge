package dev.staticsanches.kge.engine.console

import dev.staticsanches.kge.engine.input.TextInputEvent

/**
 * The editable single line: [text] with a [cursor] that never splits a code
 * point. The mutators are engine-thread only; the console owns one entry.
 */
class TextEntry internal constructor(
    private val requireEngineThread: () -> Unit = {},
) {
    /** The line as typed. */
    var text: String = ""
        private set

    /** The code-unit index of the insertion point; never inside a surrogate pair. */
    var cursor: Int = 0
        private set

    /** Whether the entry consumes input. */
    var isEnabled: Boolean = false
        private set

    /** Enables the entry with [text] and the cursor at its end; starts empty by default. */
    fun enable(text: String = "") {
        requireEngineThread()
        this.text = text
        cursor = text.length
        isEnabled = true
    }

    /** Disables the entry; the text and the cursor survive. */
    fun disable() {
        requireEngineThread()
        isEnabled = false
    }

    /** Applies one entry event; the console-owned edits (UP, DOWN, ENTER) are ignored. */
    internal fun apply(event: TextInputEvent) {
        requireEngineThread()
        when (event) {
            is TextInputEvent.Character -> insert(event.codePoint)
            TextInputEvent.Edit.LEFT -> moveLeft()
            TextInputEvent.Edit.RIGHT -> moveRight()
            TextInputEvent.Edit.BACKSPACE -> backspace()
            TextInputEvent.Edit.DELETE -> delete()
            else -> Unit
        }
    }

    private fun insert(codePoint: Int) {
        val inserted = codePointToString(codePoint)
        text = text.substring(0, cursor) + inserted + text.substring(cursor)
        cursor += inserted.length
    }

    private fun moveLeft() {
        if (cursor <= 0) return
        cursor -= codePointLengthBefore(cursor)
    }

    private fun moveRight() {
        if (cursor >= text.length) return
        cursor += codePointLengthAt(cursor)
    }

    private fun backspace() {
        if (cursor <= 0) return
        val start = cursor - codePointLengthBefore(cursor)
        text = text.removeRange(start, cursor)
        cursor = start
    }

    private fun delete() {
        if (cursor >= text.length) return
        text = text.removeRange(cursor, cursor + codePointLengthAt(cursor))
    }

    /** The code-unit length of the code point starting at [index]. */
    private fun codePointLengthAt(index: Int): Int =
        if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1

    /** The code-unit length of the code point ending at [index]. */
    private fun codePointLengthBefore(index: Int): Int =
        if (index >= 2 && text[index - 2].isHighSurrogate() && text[index - 1].isLowSurrogate()) 2 else 1
}

/** [codePoint] as a string; a supplementary code point becomes its surrogate pair. */
private fun codePointToString(codePoint: Int): String =
    if (codePoint in 0x10000..0x10FFFF) {
        val offset = codePoint - 0x10000
        charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
    } else {
        codePoint.toChar().toString()
    }
