package dev.staticsanches.kge.engine.input

import kotlin.jvm.JvmInline

/**
 * The modifier keys held when an input event fired, plus the Caps Lock and
 * Num Lock states. The platform translates its native event modifiers into one
 * of these snapshots.
 */
@JvmInline
value class Modifiers private constructor(
    private val mask: Int,
) {
    /** Whether Shift is held. */
    val shift: Boolean
        get() = mask and SHIFT != 0

    /** Whether Control is held. */
    val ctrl: Boolean
        get() = mask and CTRL != 0

    /** Whether Alt is held. */
    val alt: Boolean
        get() = mask and ALT != 0

    /** Whether the Meta/Super/Windows key is held. */
    val superKey: Boolean
        get() = mask and SUPER != 0

    /** Whether Caps Lock is on. */
    val capsLock: Boolean
        get() = mask and CAPS_LOCK != 0

    /** Whether Num Lock is on. */
    val numLock: Boolean
        get() = mask and NUM_LOCK != 0

    companion object {
        private const val SHIFT = 1 shl 0
        private const val CTRL = 1 shl 1
        private const val ALT = 1 shl 2
        private const val SUPER = 1 shl 3
        private const val CAPS_LOCK = 1 shl 4
        private const val NUM_LOCK = 1 shl 5

        /** The snapshot for the given held modifiers and lock states. */
        fun of(
            shift: Boolean = false,
            ctrl: Boolean = false,
            alt: Boolean = false,
            superKey: Boolean = false,
            capsLock: Boolean = false,
            numLock: Boolean = false,
        ): Modifiers =
            Modifiers(
                (if (shift) SHIFT else 0) or
                    (if (ctrl) CTRL else 0) or
                    (if (alt) ALT else 0) or
                    (if (superKey) SUPER else 0) or
                    (if (capsLock) CAPS_LOCK else 0) or
                    (if (numLock) NUM_LOCK else 0),
            )
    }
}
