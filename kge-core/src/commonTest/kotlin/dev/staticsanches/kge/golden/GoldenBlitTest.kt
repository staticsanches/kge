package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec

class GoldenBlitTest :
    FunSpec({
        test("blit/nearest matches the golden") {
            source2x2().use { source ->
                canvas(4, 4).use { target ->
                    Rasterizer.blit(target, 0, 0, source, 2, Pixmap.Flip.NONE, Pixel.Mode.Normal)
                    target.shouldMatchGolden("blit/nearest")
                }
            }
        }

        test("blit/bilinear matches the golden") {
            source2x2().use { source ->
                canvas(4, 4).use { target ->
                    for (y in 0 until target.height) {
                        for (x in 0 until target.width) {
                            // destination texel center (x + 0.5) / size in uv space
                            target.set(
                                x, y,
                                source.sampleBL(
                                    (x + 0.5f) / target.width.toFloat(),
                                    (y + 0.5f) / target.height.toFloat(),
                                ),
                            )
                        }
                    }
                    target.shouldMatchGolden("blit/bilinear")
                }
            }
        }

        test("blit/region matches the golden") {
            source5x5().use { source ->
                canvas(6, 6).use { target ->
                    Rasterizer.blitRegion(
                        target, 2, 1, source, Int2D(1, 1), Int2D(2, 2), 1, Pixmap.Flip.NONE, Pixel.Mode.Normal,
                    )
                    target.shouldMatchGolden("blit/region")
                }
            }
        }
    })

private fun source2x2(): Sprite =
    SpriteService
        .create(2, 2, Pixmap.SampleMode.NORMAL, null)
        .applyClosingIfFailed {
            set(0, 0, Pixel.rgba(200, 40, 10, 255))
            set(1, 0, Pixel.rgba(20, 180, 90, 128))
            set(0, 1, Pixel.rgba(60, 30, 220, 64))
            set(1, 1, Pixel.rgba(250, 250, 250, 200))
        }

private fun source5x5(): Sprite =
    SpriteService
        .create(5, 5, Pixmap.SampleMode.NORMAL, null)
        .applyClosingIfFailed {
            for (y in 0 until 5) {
                for (x in 0 until 5) {
                    set(x, y, Pixel.rgba(20 + 40 * x, 200 - 30 * y, 10 + 25 * (x + y), 255 - 20 * x - 10 * y))
                }
            }
        }
