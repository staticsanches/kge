package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.types.vector.Int2D

interface DrawRectAddon {
    context(Window)
    fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawRect(diagonalStart, diagonalEnd, color, drawTarget ?: return, pixelMode)
    }

    context(Window)
    fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawLine(
            diagonalStartX,
            diagonalStartY,
            diagonalEndX,
            diagonalEndY,
            color,
            drawTarget ?: return,
            pixelMode,
        )
    }
}
