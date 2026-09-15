package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.LinePattern
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Draws triangle outlines into the draw target, a no-op when it is null. */
interface DrawTriangleAddon :
    HasDrawTarget,
    HasDrawModes {
    /** Draws the triangle with vertices [p0], [p1], [p2], each edge masked by [pattern]. */
    fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawTriangle(
            target = drawTarget ?: return,
            p0 = p0,
            p1 = p1,
            p2 = p2,
            color = color,
            pattern = pattern,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [drawTriangle]. */
    fun drawTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel = Colors.WHITE,
        pattern: LinePattern = LinePattern.Filled,
    ) {
        Rasterizer.drawTriangle(
            target = drawTarget ?: return,
            x0 = x0,
            y0 = y0,
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            color = color,
            pattern = pattern,
            mode = pixelMode,
        )
    }
}
