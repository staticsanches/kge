package dev.staticsanches.kge.renderer.device

/** A [GpuDevice] that records its lifecycle calls in order, with no real
 * window or GL context. */
class RecordingGpuDevice : GpuDevice {
    val calls = mutableListOf<String>()

    override fun makeCurrent() {
        calls += "makeCurrent"
    }

    override fun present() {
        calls += "present"
    }
}
