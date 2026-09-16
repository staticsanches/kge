package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.CircleOctantMask
import dev.staticsanches.kge.rasterizer.Rasterizer
import io.kotest.core.spec.style.FunSpec

class GoldenCircleTest :
    FunSpec({
        test("circle/outline-all matches the golden") {
            canvas(11, 11).use { surface ->
                Rasterizer.drawCircle(surface, 5, 5, 4, CircleOctantMask.ALL, Colors.RED, Pixel.Mode.Normal)
                surface.shouldMatchGolden("circle/outline-all")
            }
        }

        test("circle/mask-two-octants matches the golden") {
            canvas(11, 11).use { surface ->
                Rasterizer.drawCircle(
                    surface, 5, 5, 4, CircleOctantMask.O1 or CircleOctantMask.O5, Colors.RED, Pixel.Mode.Normal,
                )
                surface.shouldMatchGolden("circle/mask-two-octants")
            }
        }

        test("circle/fill matches the golden") {
            canvas(11, 11).use { surface ->
                Rasterizer.fillCircle(surface, 5, 5, 4, CircleOctantMask.ALL, Colors.RED, Pixel.Mode.Normal)
                surface.shouldMatchGolden("circle/fill")
            }
        }
    })
