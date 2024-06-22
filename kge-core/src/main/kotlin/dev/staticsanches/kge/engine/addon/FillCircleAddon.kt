package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.CircleOctantMask

interface FillCircleAddon {
    context(Window)
    fun fillCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillCircle(center, radius, mask, color, drawTarget ?: return, pixelMode)
    }

    context(Window)
    fun fillCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillCircle(centerX, centerY, radius, mask, color, drawTarget ?: return, pixelMode)
    }
}
