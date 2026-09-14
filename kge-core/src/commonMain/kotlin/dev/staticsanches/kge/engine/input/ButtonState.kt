package dev.staticsanches.kge.engine.input

import kotlin.jvm.JvmInline

/**
 * The edge state of one key or mouse button for the current frame: [pressed]
 * and [released] are set only for the frame of the transition, [held] stays set
 * on the frames in between.
 */
@JvmInline
value class ButtonState internal constructor(
    private val mask: Int,
) {
    /** Whether the button went down this frame. */
    val pressed: Boolean
        get() = mask and PRESSED != 0

    /** Whether the button went up this frame. */
    val released: Boolean
        get() = mask and RELEASED != 0

    /** Whether the button is down on this frame (set from its press through the frame before its release). */
    val held: Boolean
        get() = mask and HELD != 0

    internal companion object {
        private const val PRESSED = 0b001
        private const val RELEASED = 0b010
        private const val HELD = 0b100

        /** The state for the given edges. */
        fun of(
            pressed: Boolean,
            released: Boolean,
            held: Boolean,
        ): ButtonState =
            ButtonState(
                (if (pressed) PRESSED else 0) or
                    (if (released) RELEASED else 0) or
                    (if (held) HELD else 0),
            )
    }
}
