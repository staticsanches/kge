// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The internal dynamic staging buffer: one GL buffer is created and its
 * storage is grown only when a larger capacity is requested — a request that
 * still fits reuses the current storage instead of reallocating.
 */
class StagingBufferTest :
    FunSpec({
        test("storage is grown on demand and reused while it fits") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            StagingBuffer("test").use { staging ->
                recorder.calls.count { it.name == "createBuffer" } shouldBe 1
                recorder.calls.count { it.name == "bufferData" } shouldBe 0

                staging.ensureCapacity(64)
                staging.ensureCapacity(32)

                recorder.calls.count { it.name == "bufferData" } shouldBe 1

                staging.ensureCapacity(128)

                recorder.calls.count { it.name == "bufferData" } shouldBe 2
            }

            recorder.calls.count { it.name == "deleteBuffer" } shouldBe 1
        }
    })
