// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.internal

import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.RecordedGLCall
import dev.staticsanches.kge.renderer.gl.RecordingGLService
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The internal dynamic staging buffer: CPU storage grows only for a larger
 * requested capacity, never touches the GPU, and each upload orphans exactly
 * the bytes a draw wrote.
 */
class StagingBufferTest :
    FunSpec({
        test("ensureCapacity grows only the CPU storage and reuses it while it fits") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            StagingBuffer("test").use { staging ->
                recorder.calls.count { it.name == "createBuffer" } shouldBe 1
                recorder.calls.count { it.name == "bufferData" } shouldBe 0

                val first = staging.ensureCapacity(64)
                (staging.ensureCapacity(32) === first) shouldBe true
                (staging.ensureCapacity(64) === first) shouldBe true

                val grown = staging.ensureCapacity(128)
                (grown === first) shouldBe false

                recorder.calls.count { it.name == "bufferData" } shouldBe 0
            }

            recorder.calls.count { it.name == "deleteBuffer" } shouldBe 1
        }

        test("upload binds the buffer and orphans exactly the requested bytes") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            StagingBuffer("test").use { staging ->
                val data = staging.ensureCapacity(64)
                recorder.clear()

                staging.upload(28)

                recorder.calls shouldBe
                    listOf(
                        RecordedGLCall("bindBuffer", listOf(GL.ARRAY_BUFFER, staging.buffer)),
                        RecordedGLCall("bufferData", listOf(GL.ARRAY_BUFFER, data.resource, 28, GL.STREAM_DRAW)),
                    )
            }
        }

        test("upload rejects a byte count outside the current capacity") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            StagingBuffer("test").use { staging ->
                staging.ensureCapacity(16)

                shouldThrow<IllegalArgumentException> { staging.upload(17) }
                shouldThrow<IllegalArgumentException> { staging.upload(-1) }
            }
        }

        test("upload before ensureCapacity fails fast") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            StagingBuffer("test").use { staging ->
                shouldThrow<IllegalStateException> { staging.upload(0) }
            }
        }
    })
