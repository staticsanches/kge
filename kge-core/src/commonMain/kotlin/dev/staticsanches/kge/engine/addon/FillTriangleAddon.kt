@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface FillTriangleAddon : WindowDependentAddon {
    fun fillTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillTriangle(
            p0 = p0,
            p1 = p1,
            p2 = p2,
            color = color,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun fillTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillTriangle(
            x0 = x0, y0 = y0,
            x1 = x1, y1 = y1,
            x2 = x2, y2 = y2,
            color = color,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }
}
