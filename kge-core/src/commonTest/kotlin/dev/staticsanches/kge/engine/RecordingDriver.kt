package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.RawInput
import dev.staticsanches.kge.math.vector.Int2D

/**
 * A [Driver] with no window or GL context: it records the lifecycle calls in
 * order and reports the scripted closing state and framebuffer size. Its
 * [input] is scriptable between frames.
 */
internal class RecordingDriver(
    var scriptedClosing: Boolean = false,
    var scriptedFramebufferSize: Int2D = Int2D(0, 0),
    var scriptedWindowSize: Int2D = Int2D(0, 0),
) : Driver {
    override val input = RawInput()

    val calls = mutableListOf<String>()

    override fun makeCurrent() {
        calls += "makeCurrent"
    }

    override fun pollEvents() {
        calls += "pollEvents"
    }

    override fun present() {
        calls += "present"
    }

    override suspend fun awaitNextFrame() {
        calls += "awaitNextFrame"
    }

    override fun windowSize(): Int2D = scriptedWindowSize

    override fun framebufferSize(): Int2D = scriptedFramebufferSize

    override fun isClosing(): Boolean = scriptedClosing

    override fun cancelClose() {
        calls += "cancelClose"
        scriptedClosing = false
    }

    override fun close() {
        calls += "close"
    }
}
