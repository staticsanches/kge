@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawLineService.LinePattern

interface DrawRectAddon : WindowDependentAddon {
    fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawRect(
            diagonalStart = diagonalStart,
            diagonalEnd = diagonalEnd,
            color = color,
            pattern = pattern,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawLine(
            startX = diagonalStartX,
            startY = diagonalStartY,
            endX = diagonalEndX,
            endY = diagonalEndY,
            color = color,
            pattern = pattern,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }
}
