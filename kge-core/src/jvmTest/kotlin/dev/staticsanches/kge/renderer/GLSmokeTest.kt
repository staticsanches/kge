package dev.staticsanches.kge.renderer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33
import org.lwjgl.system.MemoryUtil.memUTF8
import java.nio.ByteBuffer

/**
 * SPIKE probe (throwaway): can a JVM unit test create a headless GL context and
 * read a pixel back? Creates a hidden GLFW window with an OpenGL 3.3 core
 * context, renders into an off-screen framebuffer and reads one pixel.
 *
 * The point is to find out what the jvmTest task needs on each CI OS (macOS
 * main-thread rule, ubuntu display/xvfb, GL version availability), not to pin
 * an engine API. Delete or promote to the C9 harness once the facts are in.
 */
class GLSmokeTest :
    FunSpec({
        test("hidden GLFW window + GL 3.3 context clears an FBO and reads it back") {
            val glfwError = arrayOfNulls<String>(1)
            GLFW.glfwSetErrorCallback { code, description ->
                glfwError[0] = "GLFW error $code: ${memUTF8(description)}"
            }

            check(GLFW.glfwInit()) { glfwError[0] ?: "glfwInit returned false" }
            try {
                GLFW.glfwDefaultWindowHints()
                GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)

                val window = GLFW.glfwCreateWindow(64, 64, "kge-gl-smoke", 0L, 0L)
                check(window != 0L) { glfwError[0] ?: "glfwCreateWindow returned 0" }
                try {
                    GLFW.glfwMakeContextCurrent(window)
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
                } finally {
                    GLFW.glfwDestroyWindow(window)
                }
            } finally {
                GLFW.glfwTerminate()
            }
        }
    })
