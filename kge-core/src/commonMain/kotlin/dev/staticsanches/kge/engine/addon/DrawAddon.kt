@file:Suppress("unused")

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
        return Rasterizer.draw(
            position = position,
            color = color,
            target = drawTarget ?: return false,
            pixelMode = pixelMode,
        )
    }

    fun draw(
        x: Int,
        y: Int,
        color: Pixel = Colors.WHITE,
    ): Boolean {
        return Rasterizer.draw(
            x = x, y = y,
            color = color,
            target = drawTarget ?: return false,
            pixelMode = pixelMode,
        )
    }
}
