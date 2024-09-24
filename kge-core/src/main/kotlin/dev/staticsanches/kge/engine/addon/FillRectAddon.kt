package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface FillRectAddon : WindowDependentAddon {
    fun fillRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillRect(diagonalStart, diagonalEnd, color, drawTarget ?: return, pixelMode)
    }

    fun fillRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillRect(
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
