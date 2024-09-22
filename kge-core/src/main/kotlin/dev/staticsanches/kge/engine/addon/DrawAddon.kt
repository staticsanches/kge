package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawAddon : WindowDependentAddon {
    fun draw(
        position: Int2D,
        color: Pixel = Colors.WHITE,
    ): Boolean {
        return Rasterizer.draw(position, color, drawTarget ?: return false, pixelMode)
    }

    fun draw(
        x: Int,
        y: Int,
        color: Pixel = Colors.WHITE,
    ): Boolean {
        return Rasterizer.draw(x, y, color, drawTarget ?: return false, pixelMode)
    }
}
