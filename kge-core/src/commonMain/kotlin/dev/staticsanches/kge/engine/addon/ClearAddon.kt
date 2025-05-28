@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer

interface ClearAddon : WindowDependentAddon {
    fun clear(pixel: Pixel) {
        Rasterizer.clear(pixel = pixel, target = drawTarget ?: return)
    }

    fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
        Rasterizer.clear(pixelByXY = pixelByXY, target = drawTarget ?: return)
    }

    fun clear(pixels: Iterable<Pixel>) {
        Rasterizer.clear(pixels = pixels, target = drawTarget ?: return)
    }
}
