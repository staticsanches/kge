package dev.staticsanches.kge.renderer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33
import java.nio.ByteBuffer

private val glContext: GlTestContext? = GlTestContext.detect()

/**
 * SPIKE probe (throwaway): can a JVM unit test get a GL context with no
 * display and read a pixel back? Renders into an off-screen framebuffer and
 * reads one pixel. Skips (disabled) when no backend is available, which is the
 * case on the GitHub macOS/Windows runners. Delete or promote to the C9 harness
 * once the facts are in.
 */
class GLSmokeTest :
    FunSpec({
        test("offscreen GL context (${glContext?.backend ?: "unavailable"}) clears an FBO and reads a pixel back")
            .config(enabled = glContext != null) {
                glContext!!.use { context ->
                    context.makeCurrent()
                    GL.createCapabilities()

                    val framebuffer = GL33.glGenFramebuffers()
                    val texture = GL33.glGenTextures()
                    try {
                        GL33.glBindTexture(GL33.GL_TEXTURE_2D, texture)
                        GL33.glTexImage2D(
                            GL33.GL_TEXTURE_2D,
                            0,
                            GL33.GL_RGBA8,
                            64,
                            64,
                            0,
                            GL33.GL_RGBA,
                            GL33.GL_UNSIGNED_BYTE,
                            null as ByteBuffer?,
                        )
                        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, framebuffer)
                        GL33.glFramebufferTexture2D(
                            GL33.GL_FRAMEBUFFER,
                            GL33.GL_COLOR_ATTACHMENT0,
                            GL33.GL_TEXTURE_2D,
                            texture,
                            0,
                        )
                        GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER) shouldBe GL33.GL_FRAMEBUFFER_COMPLETE

                        GL33.glViewport(0, 0, 64, 64)
                        GL33.glClearColor(1f, 0f, 0f, 1f)
                        GL33.glClear(GL33.GL_COLOR_BUFFER_BIT)

                        val pixel = ByteBuffer.allocateDirect(4)
                        GL33.glReadPixels(0, 0, 1, 1, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, pixel)
                        (pixel.get(0).toInt() and 0xFF) shouldBe 0xFF
                        (pixel.get(1).toInt() and 0xFF) shouldBe 0x00
                        (pixel.get(2).toInt() and 0xFF) shouldBe 0x00
                    } finally {
                        GL33.glDeleteTextures(texture)
                        GL33.glDeleteFramebuffers(framebuffer)
                    }
                }
            }
    })
