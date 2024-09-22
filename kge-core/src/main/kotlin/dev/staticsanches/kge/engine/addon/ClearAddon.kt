package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer

interface ClearAddon : WindowDependentAddon {
    fun clear(pixel: Pixel) {
        Rasterizer.clear(pixel, drawTarget ?: return)
    }

    fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
        Rasterizer.clear(pixelByXY, drawTarget ?: return)
    }
}
