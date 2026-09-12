package dev.staticsanches.kge.renderer.device

import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The device seam contract: [GpuDevice] only makes a context current and
 * presents — it has no window, viewport or GL knowledge. The seam and
 * [GLService] are independent, so the engine loop can drive a real device
 * around the recording backend on any target.
 */
class GpuDeviceTest :
    FunSpec({
        test("makeCurrent and present are observed in order, independent of GL") {
            val device = RecordingGpuDevice()
            val recorder = RecordingGLService()
            GLService.override(recorder)

            device.makeCurrent()
            GLService.clearColor(1f, 0f, 0f, 1f)
            device.present()

            device.calls shouldBe listOf("makeCurrent", "present")
            recorder.calls shouldBe listOf(RecordedGLCall("clearColor", listOf(1f, 0f, 0f, 1f)))
        }
    })
