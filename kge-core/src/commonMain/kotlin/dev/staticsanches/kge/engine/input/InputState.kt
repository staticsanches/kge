package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.math.vector.Int2D

/**
 * The input snapshot of the current frame: the edge state of every key and
 * mouse button, the pointer in screen pixels, the wheel delta, the modifiers
 * and the focus.
 *
 * The engine refreshes it once per frame before the update callback, so it is
 * stable for the whole frame.
 */
class InputState internal constructor() {
    internal val keyPressed = PackedBits(KeyboardKey.entries.size)
    internal val keyReleased = PackedBits(KeyboardKey.entries.size)
    internal val keyHeld = PackedBits(KeyboardKey.entries.size)
    internal val mousePressed = PackedBits(MouseButton.entries.size)
    internal val mouseReleased = PackedBits(MouseButton.entries.size)
    internal val mouseHeld = PackedBits(MouseButton.entries.size)

    /** The pointer position in screen pixels, clamped to the screen. */
    var mousePosition: Int2D = Int2D.ZERO
        internal set

    /** The wheel delta accumulated since the previous frame. */
    var mouseWheel: Int = 0
        internal set

    /** The modifier keys held when the latest event fired. */
    var modifiers: Modifiers = Modifiers.of()
        internal set

    /** Whether the window holds input focus on this frame. */
    var focused: Boolean = false
        internal set

    /** The edge state of [key] on this frame. */
    fun key(key: KeyboardKey): ButtonState =
        ButtonState.of(keyPressed[key.ordinal], keyReleased[key.ordinal], keyHeld[key.ordinal])

    /** The edge state of [button] on this frame. */
    fun mouseButton(button: MouseButton): ButtonState =
        ButtonState.of(mousePressed[button.ordinal], mouseReleased[button.ordinal], mouseHeld[button.ordinal])
}
