package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.renderer.device.GlfwTestDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.opengl.GL33

private val glDevice: GlfwTestDevice? = GlfwTestDevice.detect()

/**
 * The real-GL smoke for the JVM platform default ([GLService] over LWJGL
 * `GL33`): create a texture and an off-screen framebuffer, clear it through the
 * service and read a pixel back into an engine buffer. The device makes the
 * hidden GLFW context current; the FBO setup is the harness's raw LWJGL (the GL
 * layer has no FBO API) and every engine command goes through the default
 * backend.
 *
 * Skips (disabled) when no backend is available — the hosted macOS runner
 * cannot create a GL context (decisions-log chunk 21).
 */
class GLSmokeTest :
    FunSpec({
        test("the JVM default clears an FBO and reads a pixel back (${glDevice?.backend ?: "unavailable"})")
            .config(enabled = glDevice != null) {
                glDevice!!.use { device ->
                    device.makeCurrent()

                    val texture = GLService.createTexture()
                    val framebuffer = GL33.glGenFramebuffers()
                    try {
                        GLService.bindTexture(GL.TEXTURE_2D, texture)
                        GLService.texImage2D(
                            GL.TEXTURE_2D,
                            0,
                            GL.RGBA,
                            64,
                            64,
                            0,
                            GL.RGBA,
                            GL.UNSIGNED_BYTE,
                            null,
                        )
                        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, framebuffer)
                        GL33.glFramebufferTexture2D(
                            GL33.GL_FRAMEBUFFER,
                            GL33.GL_COLOR_ATTACHMENT0,
                            GL33.GL_TEXTURE_2D,
                            texture.id,
                            0,
                        )
                        GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER) shouldBe GL33.GL_FRAMEBUFFER_COMPLETE

                        GLService.viewport(0, 0, 64, 64)
                        GLService.clearColor(1f, 0f, 0f, 1f)
                        GLService.clear(GL.COLOR_BUFFER_BIT)

                        BufferService.allocate(4, "gl smoke").use { pixel ->
                            GLService.readPixels(0, 0, 1, 1, GL.RGBA, GL.UNSIGNED_BYTE, pixel.resource)
                            pixel.resource.byteAt(0) shouldBe 0xFF
                            pixel.resource.byteAt(1) shouldBe 0x00
                            pixel.resource.byteAt(2) shouldBe 0x00
                            pixel.resource.byteAt(3) shouldBe 0xFF
                        }
                    } finally {
                        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, 0)
                        GL33.glDeleteFramebuffers(framebuffer)
                        GLService.deleteTexture(texture)
                    }
                }
            }
    })
