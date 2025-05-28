@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawLineService.LinePattern

interface DrawLineAddon : WindowDependentAddon {
    fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawLine(
            start = start,
            end = end,
            color = color,
            pattern = pattern,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawLine(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            color = color,
            pattern = pattern,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }
}
