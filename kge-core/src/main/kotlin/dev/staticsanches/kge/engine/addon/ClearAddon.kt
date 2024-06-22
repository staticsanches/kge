package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer

interface ClearAddon {
    context(Window)
    fun clear(pixel: Pixel) {
        Rasterizer.clear(pixel, drawTarget ?: return)
    }

    context(Window)
    fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
        Rasterizer.clear(pixelByXY, drawTarget ?: return)
    }
}
