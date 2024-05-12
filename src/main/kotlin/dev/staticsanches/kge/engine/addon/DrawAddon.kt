package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.types.vector.Int2D

interface DrawAddon {
    context(Window)
    fun draw(
        position: Int2D,
        color: Pixel = Colors.WHITE,
    ): Boolean {
        return Rasterizer.draw(position, color, drawTarget ?: return false, pixelMode)
    }

    context(Window)
    fun draw(
        x: Int,
        y: Int,
        color: Pixel = Colors.WHITE,
    ): Boolean {
        return Rasterizer.draw(x, y, color, drawTarget ?: return false, pixelMode)
    }
}
