// The handles are `web.gl` interop types on the web targets; transporting them
// through the common recording assertions is the intended, safe use.
@file:Suppress("OPT_IN_USAGE")

package dev.staticsanches.kge.renderer.gl

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.gl.service.glServiceDefault
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The GL seam contract at step 0: the [GL] namespace and the [GLService]
 * facade both resolve the overridable backend per call, the recording backend
 * captures the raw commands, fabricated handles survive the round-trip, and
 * `resetAll` leaves the platform backend in place as the engine default.
 */
class GLServiceTest :
    FunSpec({
        test("an override is observed by both GL and the GLService facade") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            val texture = recordingTextureHandle(1)
            GL.bindTexture(GL.TEXTURE_2D, texture)
            GLService.bindTexture(GL.TEXTURE_2D, texture)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, texture)),
                    RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, texture)),
                )
        }

        test("fabricated handles round-trip through the recorder") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            val texture = recordingTextureHandle(11)
            val buffer = recordingBufferHandle(12)
            val shader = recordingShaderHandle(13)
            val program = recordingProgramHandle(14)
            val vao = recordingVertexArrayHandle(15)
            val location = recordingUniformLocationHandle(16)

            GL.bindTexture(GL.TEXTURE_2D, texture)
            GL.bindBuffer(GL.ARRAY_BUFFER, buffer)
            GL.attachShader(program, shader)
            GL.bindVertexArray(vao)
            GL.uniform1i(location, 3)

            recorder.calls shouldBe
                listOf(
                    RecordedGLCall("bindTexture", listOf(GL.TEXTURE_2D, texture)),
                    RecordedGLCall("bindBuffer", listOf(GL.ARRAY_BUFFER, buffer)),
                    RecordedGLCall("attachShader", listOf(program, shader)),
                    RecordedGLCall("bindVertexArray", listOf(vao)),
                    RecordedGLCall("uniform1i", listOf(location, 3)),
                )
        }

        test("getTexImage records the level, region and destination") {
            val recorder = RecordingGLService()
            GLService.override(recorder)

            BufferService.allocate(16, "getTexImage test").use { dst ->
                GLService.getTexImage(GL.TEXTURE_2D, 0, 2, 2, GL.RGBA, GL.UNSIGNED_BYTE, dst.resource)

                recorder.calls shouldBe
                    listOf(
                        RecordedGLCall(
                            "getTexImage",
                            listOf(GL.TEXTURE_2D, 0, 2, 2, GL.RGBA, GL.UNSIGNED_BYTE, dst.resource),
                        ),
                    )
            }
        }

        test("resetAll leaves the platform GL backend as the engine default") {
            GLService.override(RecordingGLService())
            GLService.createTexture()

            KGEOverridable.Proxy.resetAll()

            // The real platform backend needs a current GL context, so its
            // restoration is asserted by identity rather than invoked (an
            // un-contextualized call aborts the JVM inside the driver).
            GLService.original shouldBe glServiceDefault
        }

        test("the GLenum constants carry the GL values") {
            val expected =
                listOf(
                    GL.ZERO to 0,
                    GL.ONE to 1,
                    GL.SRC_ALPHA to 0x0302,
                    GL.ONE_MINUS_SRC_ALPHA to 0x0303,
                    GL.TEXTURE_2D to 0x0DE1,
                    GL.UNSIGNED_BYTE to 0x1401,
                    GL.FLOAT to 0x1406,
                    GL.RGBA to 0x1908,
                    GL.NEAREST to 0x2600,
                    GL.LINEAR to 0x2601,
                    GL.TEXTURE_MAG_FILTER to 0x2800,
                    GL.TEXTURE_MIN_FILTER to 0x2801,
                    GL.TEXTURE_WRAP_S to 0x2802,
                    GL.TEXTURE_WRAP_T to 0x2803,
                    GL.REPEAT to 0x2901,
                    GL.CLAMP_TO_EDGE to 0x812F,
                    GL.FRAGMENT_SHADER to 0x8B30,
                    GL.VERTEX_SHADER to 0x8B31,
                    GL.ARRAY_BUFFER to 0x8892,
                    GL.STREAM_DRAW to 0x88E0,
                    GL.STATIC_DRAW to 0x88E4,
                    GL.DYNAMIC_DRAW to 0x88E8,
                    GL.COLOR_BUFFER_BIT to 0x00004000,
                    GL.TRIANGLES to 0x0004,
                    GL.TRIANGLE_STRIP to 0x0005,
                    GL.TRIANGLE_FAN to 0x0006,
                    GL.BLEND to 0x0BE2,
                    GL.DEPTH_TEST to 0x0B71,
                    GL.LESS to 0x0201,
                    GL.LEQUAL to 0x0203,
                )

            expected.forEach { (actual, value) -> actual shouldBe value }
        }
    })
