@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.device.WebGlTestDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.renderer.internal.DefaultRenderer
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.js.ExperimentalWasmJsInterop

private const val SMOKE_SIZE = 8
private val SMOKE_COLOR = Pixel.rgba(0x3366CCFFu)

/**
 * The real-GL renderer smoke for the web platform default: create a source
 * texture through the default [Renderer], upload a solid-color [Sprite], read it
 * back through the backend's framebuffer fallback, render it with a full-screen
 * layer quad onto the canvas and read one pixel back. Runs on both browser
 * targets (js + wasmJs).
 *
 * The renderer is a test-local service override, so its built-in program/buffer
 * are never shared across the process's GL contexts and are released through the
 * test-local `ResourceScope`.
 */
class WebRendererSmokeTest :
    FunSpec({
        test("the web default renders a textured layer quad") {
            WebGlTestDevice.create(width = SMOKE_SIZE, height = SMOKE_SIZE).use { device ->
                device.makeCurrent()

                val renderer = DefaultRenderer()
                Renderer.override(renderer)
                val scope = ResourceScope()
                renderer.createResources(device, scope)
                try {
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
                    scope.close()
                }
            }
        }
    })
