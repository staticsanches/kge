package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.service.DrawDecalService
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
 * The real-GL decal smoke for the JVM platform default: create a decal from a
 * solid-color [Sprite], build its `DecalInstance` with the common draw service,
 * render it into an off-screen framebuffer and read one pixel back. Exercises
 * decal creation, upload, the built-in program and the real GL33 backend.
 *
 * The renderer is a test-local service override, so its built-in program/buffer
 * are never shared across the process's GL contexts. Skips when no context is
 * available (the hosted macOS runner cannot create one).
 */
class DecalSmokeTest :
    FunSpec({
        test("the JVM default renders a solid decal (${smokeDevice?.backend ?: "unavailable"})")
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

                        SpriteService
                            .create(SMOKE_SIZE, SMOKE_SIZE, Pixmap.SampleMode.NORMAL, "decal smoke")
                            .use { sprite ->
                                sprite.clear(SMOKE_COLOR)
                                Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE).use { decal ->
                                    renderer.prepareDrawing(scope)
                                    renderer.drawDecal(
                                        scope,
                                        DrawDecalService.drawDecal(
                                            position = Float2D(0f, 0f),
                                            decal = decal,
                                            scale = Float2D(1f, 1f),
                                            tint = Colors.WHITE,
                                            mode = Decal.Mode.NORMAL,
                                            structure = Decal.Structure.FAN,
                                            viewport = Int2D(SMOKE_SIZE, SMOKE_SIZE),
                                        ),
                                    )
                                }
                            }

                        BufferService.allocate(4, "decal smoke").use { pixel ->
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
