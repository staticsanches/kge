package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer
import io.kotest.core.spec.style.FunSpec

class GoldenRectTriangleTest :
    FunSpec({
        test("rect/outline-filled matches the golden") {
            canvas(6, 6).use { surface ->
                Rasterizer.drawRect(surface, 1, 1, 4, 4, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                surface.shouldMatchGolden("rect/outline-filled")
            }
        }

        test("rect/fill matches the golden") {
            canvas(6, 6).use { surface ->
                Rasterizer.fillRect(surface, 1, 1, 4, 4, Colors.RED, Pixel.Mode.Normal)
                surface.shouldMatchGolden("rect/fill")
            }
        }

        test("triangle/outline-filled matches the golden") {
            canvas(5, 4).use { surface ->
                Rasterizer.drawTriangle(surface, 0, 0, 4, 0, 0, 3, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                surface.shouldMatchGolden("triangle/outline-filled")
            }
        }

        test("triangle/fill matches the golden") {
            canvas(4, 3).use { surface ->
                Rasterizer.fillTriangle(surface, 0, 0, 0, 2, 3, 2, Colors.RED, Pixel.Mode.Normal)
                surface.shouldMatchGolden("triangle/fill")
            }
        }
    })
