package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Draws lines into the draw target, a no-op when it is null. */
interface DrawLineAddon :
    HasDrawTarget,
    HasDrawModes {
    /** Draws the line from [start] to [end], masked by [pattern]. */
    fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawLine(
            target = drawTarget ?: return,
            start = start,
            end = end,
            color = color,
            pattern = pattern,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [drawLine]. */
    fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawLine(
            target = drawTarget ?: return,
            x0 = startX,
            y0 = startY,
            x1 = endX,
            y1 = endY,
            color = color,
            pattern = pattern,
            mode = pixelMode,
        )
    }
}
