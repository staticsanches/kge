package dev.staticsanches.kge.renderer.device

/**
 * A recording [GpuDevice] that captures its lifecycle calls in order, so a test
 * can observe that a context was made current and a frame presented without a
 * real window or GL context.
 */
internal class RecordingGpuDevice : GpuDevice {
    val calls = mutableListOf<String>()

    override fun makeCurrent() {
        calls += "makeCurrent"
    }

    override fun present() {
        calls += "present"
    }
}
