@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.CircleOctantMask

interface FillCircleAddon : WindowDependentAddon {
    fun fillCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillCircle(
            center = center,
            radius = radius,
            mask = mask,
            color = color,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun fillCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillCircle(
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
