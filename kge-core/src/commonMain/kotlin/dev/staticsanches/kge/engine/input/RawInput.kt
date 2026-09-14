package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Int2D

/**
 * The mutable input state a platform driver fills from its callbacks before the
 * engine reads a frame.
 *
 * The mutators are the driver seam: only a backend writes them, on the engine
 * thread. The readers are unrestricted.
 */
class RawInput {
    private val keyDown = PackedBits(KeyboardKey.entries.size)
    private val mouseButtonDown = PackedBits(MouseButton.entries.size)

    /** Whether [key] is currently down. */
    fun isKeyDown(key: KeyboardKey): Boolean = keyDown[key.ordinal]

    /** Sets the down state of [key]. */
    @KGESensitiveAPI
    fun setKeyDown(
        key: KeyboardKey,
        down: Boolean,
    ) {
        keyDown[key.ordinal] = down
    }

    /** Clears every key-down bit; the next frame then reports the release of any held key. */
    @KGESensitiveAPI
    fun clearKeys() {
        keyDown.clear()
    }

    /** Whether [button] is currently down. */
    fun isMouseButtonDown(button: MouseButton): Boolean = mouseButtonDown[button.ordinal]

    /** Sets the down state of [button]. */
    @KGESensitiveAPI
    fun setMouseButtonDown(
        button: MouseButton,
        down: Boolean,
    ) {
        mouseButtonDown[button.ordinal] = down
    }

    /** Clears every mouse-button-down bit; the next frame then reports their releases. */
    @KGESensitiveAPI
    fun clearMouseButtons() {
        mouseButtonDown.clear()
    }

    /** The pointer position, in window points. */
    var mousePosition: Int2D = Int2D.ZERO
        @KGESensitiveAPI set

    /** The wheel delta accumulated since the previous read. */
    var wheelDelta: Int = 0
        @KGESensitiveAPI set

    /** The modifiers reported with the latest event. */
    var modifiers: Modifiers = Modifiers.of()
        @KGESensitiveAPI set

    /** Whether the window currently holds input focus. */
    var focused: Boolean = false
        @KGESensitiveAPI set
}
