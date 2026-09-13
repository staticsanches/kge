package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.renderer.device.GlfwTestDevice
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.BuiltInQuad
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private val shaderDevice: GlfwTestDevice? = GlfwTestDevice.detect()

/**
 * The real-GL shader/program smoke for the JVM platform default: link the
 * engine's built-in program and pin that an active uniform resolves to a
 * location while an absent one normalizes to `null` (the backend maps the
 * driver's `-1` sentinel). Skips when no context is available.
 *
 * A spec of its own because closing the JVM GLFW test device terminates GLFW, so
 * a spec may own only one.
 */
class ShaderProgramSmokeTest :
    FunSpec({
        test("an inactive uniform normalizes to null (${shaderDevice?.backend ?: "unavailable"})")
            .config(enabled = shaderDevice != null) {
                shaderDevice!!.use { device ->
                    device.makeCurrent()

                    val quad = BuiltInQuad(device)
                    try {
                        GLService.getUniformLocation(quad.programHandle, "sprTex") shouldNotBe null
                        GLService.getUniformLocation(quad.programHandle, "kgeNoSuchUniform") shouldBe null
                    } finally {
                        quad.close()
                    }
                }
            }
    })
