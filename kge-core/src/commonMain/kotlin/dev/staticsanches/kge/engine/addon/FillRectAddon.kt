package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Fills rectangles into the draw target, a no-op when it is null. */
interface FillRectAddon :
    HasDrawTarget,
    HasDrawModes {
    /** Fills the inclusive box between [diagonalStart] and [diagonalEnd] with [color]. */
    fun fillRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillRect(
            target = drawTarget ?: return,
            diagonalStart = diagonalStart,
            diagonalEnd = diagonalEnd,
            color = color,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [fillRect]. */
    fun fillRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillRect(
            target = drawTarget ?: return,
            x0 = diagonalStartX,
            y0 = diagonalStartY,
            x1 = diagonalEndX,
            y1 = diagonalEndY,
            color = color,
            mode = pixelMode,
        )
    }
}
