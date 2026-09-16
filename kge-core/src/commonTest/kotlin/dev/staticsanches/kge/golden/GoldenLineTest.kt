package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer
import io.kotest.core.spec.style.FunSpec

class GoldenLineTest :
    FunSpec({
        test("line/filled matches the golden") {
            canvas(5, 3).use { surface ->
                Rasterizer.drawLine(surface, 0, 0, 4, 2, Colors.RED, LinePattern.Filled, Pixel.Mode.Normal)
                surface.shouldMatchGolden("line/filled")
            }
        }

        test("line/dotted matches the golden") {
            canvas(5, 3).use { surface ->
                Rasterizer.drawLine(surface, 0, 0, 4, 2, Colors.RED, LinePattern.Dotted(), Pixel.Mode.Normal)
                surface.shouldMatchGolden("line/dotted")
            }
        }

        test("line/clipped matches the golden") {
            canvas(4, 4).use { surface ->
                Rasterizer.drawLine(surface, -3, -1, 3, 2, Colors.RED, LinePattern.Dotted(), Pixel.Mode.Normal)
                surface.shouldMatchGolden("line/clipped")
            }
        }
    })
