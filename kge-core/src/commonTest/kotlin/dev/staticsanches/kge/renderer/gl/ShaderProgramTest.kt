// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The raw shader/program command seam: every command reaches the overridable
 * backend with its arguments in order, and the `create*` handles round-trip.
 * The real-backend checks (compile/link failure with the info log,
 * `getUniformLocation` normalizing the inactive-uniform sentinel to `null`) are
 * platform behavior pinned by the real-GL step, not by the recorder.
 */
class ShaderProgramTest :
    FunSpec({
        fun recordingService(): RecordingGLService {
            val recorder = RecordingGLService()
            GLService.override(recorder)
            return recorder
        }

        test("the shader and program commands record in order with their arguments") {
            val recorder = recordingService()

            val shader = GLService.createShader(GL.VERTEX_SHADER)
            val program = GLService.createProgram()
            GLService.shaderSource(shader, "void main() {}")
            GLService.compileShader(shader)
            GLService.attachShader(program, shader)
            GLService.linkProgram(program)
            GLService.useProgram(program)

            recorder.lastCreatedShader shouldBe shader
            recorder.lastCreatedProgram shouldBe program
            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("createShader", listOf(GL.VERTEX_SHADER)),
                    RecordedGLCall("createProgram", emptyList()),
                    RecordedGLCall("shaderSource", listOf(shader, "void main() {}")),
                    RecordedGLCall("compileShader", listOf(shader)),
                    RecordedGLCall("attachShader", listOf(program, shader)),
                    RecordedGLCall("linkProgram", listOf(program)),
                    RecordedGLCall("useProgram", listOf(program)),
                )
        }

        test("useProgram records an unbind as a null program") {
            val recorder = recordingService()

            GLService.useProgram(null)

            recorder.calls shouldBe listOf(RecordedGLCall("useProgram", listOf(null)))
        }

        test("deleteShader and deleteProgram record their handles") {
            val recorder = recordingService()
            val shader = GLService.createShader(GL.FRAGMENT_SHADER)
            val program = GLService.createProgram()

            recorder.clear()
            GLService.deleteShader(shader)
            GLService.deleteProgram(program)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("deleteShader", listOf(shader)),
                    RecordedGLCall("deleteProgram", listOf(program)),
                )
        }

        test("getUniformLocation records its result and uniform1i records the assignment") {
            val recorder = recordingService()
            val program = GLService.createProgram()

            val location = checkNotNull(GLService.getUniformLocation(program, "sampler"))
            GLService.uniform1i(location, 2)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("createProgram", emptyList()),
                    RecordedGLCall("getUniformLocation", listOf(program, "sampler", location)),
                    RecordedGLCall("uniform1i", listOf(location, 2)),
                )
        }
    })
