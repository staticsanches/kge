package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Draws rectangle outlines into the draw target, a no-op when it is null. */
interface DrawRectAddon :
    HasDrawTarget,
    HasDrawModes {
    /** Draws the perimeter of the inclusive box between [diagonalStart] and [diagonalEnd]. */
    fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawRect(
            target = drawTarget ?: return,
            diagonalStart = diagonalStart,
            diagonalEnd = diagonalEnd,
            color = color,
            pattern = pattern,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [drawRect]. */
    fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawRect(
            target = drawTarget ?: return,
            x0 = diagonalStartX,
            y0 = diagonalStartY,
            x1 = diagonalEndX,
            y1 = diagonalEndY,
            color = color,
            pattern = pattern,
            mode = pixelMode,
        )
    }
}
