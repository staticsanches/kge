package dev.staticsanches.kge.engine.input

/** One text-input event a platform driver reports, in the order it happened. */
sealed interface TextInputEvent {
    /** A character the platform reported, as a Unicode code point. */
    data class Character(
        val codePoint: Int,
    ) : TextInputEvent

    /** An edit key; a platform reports a press, never a release. */
    enum class Edit : TextInputEvent {
        LEFT,
        RIGHT,
        BACKSPACE,
        DELETE,
        UP,
        DOWN,
        ENTER,
    }
}

/** The edit operation [key] reports, or `null` when the key is not an edit. */
internal fun editForKey(key: KeyboardKey): TextInputEvent.Edit? =
    when (key) {
        KeyboardKey.left -> TextInputEvent.Edit.LEFT
        KeyboardKey.right -> TextInputEvent.Edit.RIGHT
        KeyboardKey.backspace -> TextInputEvent.Edit.BACKSPACE
        KeyboardKey.delete -> TextInputEvent.Edit.DELETE
        KeyboardKey.up -> TextInputEvent.Edit.UP
        KeyboardKey.down -> TextInputEvent.Edit.DOWN
        KeyboardKey.enter -> TextInputEvent.Edit.ENTER
        else -> null
    }
