@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.CircleOctantMask

interface DrawCircleAddon : WindowDependentAddon {
    fun drawCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawCircle(
            center = center,
            radius = radius,
            mask = mask,
            color = color,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawCircle(
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            mask = mask,
            color = color,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }
}
