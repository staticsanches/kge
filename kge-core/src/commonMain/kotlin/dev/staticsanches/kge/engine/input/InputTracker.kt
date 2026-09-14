package dev.staticsanches.kge.engine.input

import dev.staticsanches.kge.engine.ViewportFit
import dev.staticsanches.kge.math.vector.Int2D

/**
 * Turns the platform's raw state into the per-frame [InputState]: it computes
 * the press/release edges once per frame and maps the pointer to screen pixels.
 */
internal class InputTracker {
    val state = InputState()

    private val previousKeyDown = PackedBits(KeyboardKey.entries.size)
    private val previousMouseDown = PackedBits(MouseButton.entries.size)

    /** Latches one frame from [raw]; consumes the accumulated wheel delta. */
    fun latch(
        raw: RawInput,
        screenSize: Int2D,
        fit: ViewportFit,
        windowSize: Int2D,
        framebufferSize: Int2D,
    ) {
        for (key in KeyboardKey.entries) {
            scan(
                index = key.ordinal,
                down = raw.isKeyDown(key),
                previous = previousKeyDown,
                pressed = state.keyPressed,
                released = state.keyReleased,
                held = state.keyHeld,
            )
        }
        for (button in MouseButton.entries) {
            scan(
                index = button.ordinal,
                down = raw.isMouseButtonDown(button),
                previous = previousMouseDown,
                pressed = state.mousePressed,
                released = state.mouseReleased,
                held = state.mouseHeld,
            )
        }

        state.mousePosition = mapMouseToScreen(raw.mousePosition, screenSize, fit, windowSize, framebufferSize)
        state.mouseWheel = raw.wheelDelta
        raw.wheelDelta = 0
        state.modifiers = raw.modifiers
        state.focused = raw.focused
    }

    private fun scan(
        index: Int,
        down: Boolean,
        previous: PackedBits,
        pressed: PackedBits,
        released: PackedBits,
        held: PackedBits,
    ) {
        val wasDown = previous[index]
        pressed[index] = false
        released[index] = false
        if (down != wasDown) {
            if (down) {
                pressed[index] = !held[index]
                held[index] = true
            } else {
                released[index] = true
                held[index] = false
            }
        }
        previous[index] = down
    }
}

/**
 * Maps a pointer in window points to screen pixels through the letterbox, the
 * way olc maps `vMousePos`; the result is clamped to the screen.
 */
internal fun mapMouseToScreen(
    mouse: Int2D,
    screenSize: Int2D,
    fit: ViewportFit,
    windowSize: Int2D,
    framebufferSize: Int2D,
): Int2D {
    if (windowSize.x <= 0 || windowSize.y <= 0 || fit.size.x <= 0 || fit.size.y <= 0) return Int2D.ZERO

    val framebufferX = mouse.x.toDouble() * framebufferSize.x / windowSize.x
    val framebufferY = mouse.y.toDouble() * framebufferSize.y / windowSize.y
    val screenX = (framebufferX - fit.position.x) / fit.size.x * screenSize.x
    val screenY = (framebufferY - fit.position.y) / fit.size.y * screenSize.y
    return Int2D(
        screenX.toInt().coerceIn(0, screenSize.x - 1),
        screenY.toInt().coerceIn(0, screenSize.y - 1),
    )
}
