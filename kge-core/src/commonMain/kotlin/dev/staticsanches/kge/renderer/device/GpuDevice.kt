package dev.staticsanches.kge.renderer.device

/**
 * The external device/context seam the renderer operates on.
 *
 * The renderer never owns a context: the owner makes it current before the
 * renderer issues commands and presents it afterwards. [makeCurrent] is called
 * before every batch of commands, [present] once the frame is complete; the
 * caller owns the lifecycle and the renderer uses only a current device.
 */
interface GpuDevice {
    /** Makes this device's context current on the calling thread. */
    fun makeCurrent()

    /** Presents the frame that was drawn since the last [makeCurrent]. */
    fun present()
}
