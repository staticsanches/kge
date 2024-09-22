package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawTriangleAddon : WindowDependentAddon {
    fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawTriangle(p0, p1, p2, color, drawTarget ?: return, pixelMode)
    }

    fun drawTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawTriangle(x0, y0, x1, y1, x2, y2, color, drawTarget ?: return, pixelMode)
    }
}
