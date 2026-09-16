package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.core.spec.style.FunSpec

class GoldenBlendClipTest :
    FunSpec({
        test("blend/alpha matches the golden") {
            patternSurface(4, 2).use { surface ->
                Rasterizer.fillRect(surface, 0, 0, 3, 1, Pixel.rgba(200, 50, 100, 128), Pixel.Mode.Alpha())
                surface.shouldMatchGolden("blend/alpha")
            }
        }

        test("blend/mask matches the golden") {
            patternSurface(4, 2).use { surface ->
                Rasterizer.fillRect(surface, 0, 0, 1, 1, Pixel.rgba(9, 200, 30, 128), Pixel.Mode.Mask)
                Rasterizer.fillRect(surface, 2, 0, 3, 1, Pixel.rgba(10, 20, 240, 255), Pixel.Mode.Mask)
                surface.shouldMatchGolden("blend/mask")
            }
        }

        test("clip/draw-line matches the golden") {
            patternSurface(8, 8).use { surface ->
                Rasterizer.drawLine(
                    surface.window(Int2D(2, 2), Int2D(4, 4)),
                    -1, -1, 5, 3,
                    Pixel.rgba(220, 30, 40, 255),
                    LinePattern.Filled,
                    Pixel.Mode.Normal,
                )
                surface.shouldMatchGolden("clip/draw-line")
            }
        }
    })

private fun patternSurface(
    width: Int,
    height: Int,
): Sprite =
    SpriteService
        .create(width, height, Pixmap.SampleMode.NORMAL, null)
        .applyClosingIfFailed {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    set(x, y, Pixel.rgba(20 + 15 * x, 30 + 20 * y, 200 - 10 * (x + y), 255))
                }
            }
        }
