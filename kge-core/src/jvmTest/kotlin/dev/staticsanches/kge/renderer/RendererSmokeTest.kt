package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.device.GlfwTestDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.DefaultRenderer
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.lwjgl.opengl.GL33

private val smokeDevice: GlfwTestDevice? = GlfwTestDevice.detect()

private const val SMOKE_SIZE = 8
private val SMOKE_COLOR = Pixel.rgba(0x3366CCFFu)

/**
 * The real-GL renderer smoke for the JVM platform default: create a source
 * texture through the default [Renderer], upload a solid-color [Sprite], read
 * it back through `glGetTexImage`, render it with a full-screen layer quad into
 * an off-screen framebuffer, and read one pixel back. This exercises the
 * texture + built-in program + staging buffer + draw + real GL33 backend
 * together.
 *
 * The renderer is a test-local [DefaultRenderer] (overriding the service), so
 * its built-in program/buffer are never shared across the process's GL contexts
 * and are released through the test-local `ResourceScope`. Skips (disabled)
 * when no context is available — the hosted macOS runner (decisions-log
 * chunk 21).
 */
class RendererSmokeTest :
    FunSpec({
        test("the JVM default renders a textured layer quad (${smokeDevice?.backend ?: "unavailable"})")
            .config(enabled = smokeDevice != null) {
                smokeDevice!!.use { device ->
                    device.makeCurrent()

                    val renderer = DefaultRenderer()
                    Renderer.override(renderer)
                    val scope = ResourceScope()
                    renderer.createResources(device, scope)
                    val framebuffer = GL33.glGenFramebuffers()
                    val target = GLService.createTexture()
                    try {
                        GLService.bindTexture(GL.TEXTURE_2D, target)
                        GLService.texImage2D(
                            GL.TEXTURE_2D,
                            0,
                            GL.RGBA,
                            SMOKE_SIZE,
                            SMOKE_SIZE,
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
                            target.id,
                            0,
                        )
                        GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER) shouldBe GL33.GL_FRAMEBUFFER_COMPLETE

                        GLService.viewport(0, 0, SMOKE_SIZE, SMOKE_SIZE)
                        GLService.clearColor(0f, 0f, 0f, 1f)
                        GLService.clear(GL.COLOR_BUFFER_BIT)

                        renderer
                            .createTexture(SMOKE_SIZE, SMOKE_SIZE, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                            .use { source ->
                                SpriteService
                                    .create(SMOKE_SIZE, SMOKE_SIZE, Pixmap.SampleMode.NORMAL, "renderer smoke")
                                    .use { sprite ->
                                        sprite.clear(SMOKE_COLOR)
                                        renderer.updateTexture(source, sprite)

                                        SpriteService
                                            .create(
                                                SMOKE_SIZE,
                                                SMOKE_SIZE,
                                                Pixmap.SampleMode.NORMAL,
                                                "renderer readback",
                                            ).use { readback ->
                                                renderer.readTexture(source, readback)
                                                readback.get(0, 0) shouldBe SMOKE_COLOR
                                            }

                                        renderer.prepareDrawing(scope)
                                        renderer.applyTexture(source)
                                        renderer.drawLayerQuad(scope, Float2D(0f, 0f), Float2D(1f, 1f), Colors.WHITE)
                                    }
                            }

                        BufferService.allocate(4, "renderer smoke").use { pixel ->
                            GLService.readPixels(0, 0, 1, 1, GL.RGBA, GL.UNSIGNED_BYTE, pixel.resource)
                            pixel.resource.byteAt(0) shouldBe SMOKE_COLOR.r
                            pixel.resource.byteAt(1) shouldBe SMOKE_COLOR.g
                            pixel.resource.byteAt(2) shouldBe SMOKE_COLOR.b
                            pixel.resource.byteAt(3) shouldBe SMOKE_COLOR.a
                        }
                    } finally {
                        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, 0)
                        GL33.glDeleteFramebuffers(framebuffer)
                        GLService.deleteTexture(target)
                        scope.close()
                    }
                }
            }
    })
