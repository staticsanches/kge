package dev.staticsanches.kge.renderer.device

/**
 * The external device/context seam the renderer operates on.
 *
 * The renderer never owns a window or a context: the owner (the engine's
 * window on the JVM and the browser's canvas on the web) makes its context
 * current before the renderer issues commands, and presents it afterwards.
 * Implementations are platform-specific and carry no GL or renderer knowledge.
 *
 * [makeCurrent] is called before every batch of renderer commands, [present]
 * once the frame is complete. The engine loop and the test harness own the
 * device's lifecycle; the renderer only uses a device that is already current.
 */
interface GpuDevice {
    /** Makes this device's context current on the calling thread. */
    fun makeCurrent()

    /** Presents the frame that was drawn since the last [makeCurrent]. */
    fun present()
}
