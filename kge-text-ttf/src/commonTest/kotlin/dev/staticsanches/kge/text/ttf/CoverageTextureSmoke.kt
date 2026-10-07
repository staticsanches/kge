package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService
import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceScope
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/** The square viewport the smoke draws and reads back; the device must be at least this large. */
const val SMOKE_VIEWPORT_SIZE = 32

private const val SIZE_PX = 16
private const val RGBA_CHANNELS = 4
private const val BACKGROUND_CHANNEL = 0x80

/** Opaque mid-grey: equal channels, so properly swizzled ink stays achromatic. */
private val BACKGROUND = Pixel.rgba(0x808080FFu)

/** The chart coverage blended over the background by the renderer's SRC_ALPHA blend. */
private fun expectedInk(coverage: Int): Float = coverage + (255 - coverage) * (BACKGROUND_CHANNEL / 255f)

/** The [channel] of screen pixel ([x], [y]); `glReadPixels` returns rows bottom-up. */
private fun ByteBuffer.channel(
    x: Int,
    y: Int,
    channel: Int,
): Int = byteAt(((SMOKE_VIEWPORT_SIZE - 1 - y) * SMOKE_VIEWPORT_SIZE + x) * RGBA_CHANNELS + channel)

/**
 * Draws one glyph's box through the decal path on [device] and asserts the readback
 * over the real driver: coverage ink in the box, achromatic-only on its one-pixel ring.
 */
suspend fun runCoverageTextureSmoke(device: GpuDevice) {
    device.makeCurrent()
    val scope = ResourceScope()
    Renderer.createResources(device, scope)
    try {
        GLService.viewport(0, 0, SMOKE_VIEWPORT_SIZE, SMOKE_VIEWPORT_SIZE)
        GLService.clearColor(
            BACKGROUND.r / 255f,
            BACKGROUND.g / 255f,
            BACKGROUND.b / 255f,
            BACKGROUND.a / 255f,
        )
        GLService.clear(GL.COLOR_BUFFER_BIT)

        withRobotoAtlas(SIZE_PX) { anchor ->
            val placed = anchor.placed("A")
            val origin = Int2D(4, 4)
            val boxRight = origin.x + placed.size.x
            val boxBottom = origin.y + placed.size.y

            Renderer.prepareDrawing(scope)
            Renderer.drawDecal(
                scope,
                DrawPartialDecalService.drawPartialDecal(
                    position = Float2D(origin.x.toFloat(), origin.y.toFloat()),
                    decal = anchor.decal("A"),
                    sourcePosition = Float2D(placed.source.x.toFloat(), placed.source.y.toFloat()),
                    sourceSize = Float2D(placed.size.x.toFloat(), placed.size.y.toFloat()),
                    scale = Float2D(1f, 1f),
                    tint = Colors.WHITE,
                    mode = Decal.Mode.NORMAL,
                    structure = Decal.Structure.FAN,
                    viewport = Int2D(SMOKE_VIEWPORT_SIZE, SMOKE_VIEWPORT_SIZE),
                ),
            )

            BufferService
                .allocate(SMOKE_VIEWPORT_SIZE * SMOKE_VIEWPORT_SIZE * RGBA_CHANNELS, "coverage smoke")
                .use { pixels ->
                    GLService.readPixels(
                        0,
                        0,
                        SMOKE_VIEWPORT_SIZE,
                        SMOKE_VIEWPORT_SIZE,
                        GL.RGBA,
                        GL.UNSIGNED_BYTE,
                        pixels.resource,
                    )

                    var inked = 0
                    for (y in 0 until SMOKE_VIEWPORT_SIZE) {
                        for (x in 0 until SMOKE_VIEWPORT_SIZE) {
                            val insideBox =
                                x in origin.x until boxRight && y in origin.y until boxBottom
                            val red = pixels.resource.channel(x, y, 0)
                            val green = pixels.resource.channel(x, y, 1)
                            val blue = pixels.resource.channel(x, y, 2)
                            if (!insideBox) {
                                val onBoxRing = x == boxRight || y == boxBottom
                                withClue(
                                    "outside pixel ($x, $y), box (${origin.x}, ${origin.y}) " +
                                        "+ ${placed.size}, rgba $red,$green,$blue",
                                ) {
                                    if (onBoxRing) {
                                        green shouldBe red
                                        blue shouldBe red
                                    } else {
                                        red shouldBe BACKGROUND.r
                                        green shouldBe BACKGROUND.g
                                        blue shouldBe BACKGROUND.b
                                    }
                                }
                                continue
                            }
                            val coverage =
                                anchor
                                    .chart(placed)
                                    .get(
                                        placed.source.x + x - origin.x,
                                        placed.source.y + y - origin.y,
                                    ).a
                            withClue("pixel ($x, $y), coverage $coverage, red $red") {
                                // The box samples (1, 1, 1, coverage): R8 under the swizzle, RGBA8 white-alpha.
                                green shouldBe red
                                blue shouldBe red
                                abs(red - expectedInk(coverage)) shouldBeLessThanOrEqualTo 2f
                            }
                            if (coverage > 0) inked++
                        }
                    }
                    inked shouldBeGreaterThan 0
                }
        }
    } finally {
        scope.close()
    }
}
