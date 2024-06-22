package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawLineAddon {
    context(Window)
    fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawLine(start, end, color, drawTarget ?: return, pixelMode)
    }

    context(Window)
    fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawLine(startX, startY, endX, endY, color, drawTarget ?: return, pixelMode)
    }
}
