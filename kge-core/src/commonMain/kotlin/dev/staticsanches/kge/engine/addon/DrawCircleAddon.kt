package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.CircleOctantMask
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Draws circle outlines into the draw target, a no-op when it is null. */
interface DrawCircleAddon :
    HasDrawTarget,
    HasDrawModes {
    /**
     * Draws the [mask] octants of the circle of [radius] around [center] in
     * [color].
     */
    fun drawCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawCircle(
            target = drawTarget ?: return,
            center = center,
            radius = radius,
            mask = mask,
            color = color,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [drawCircle]. */
    fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.drawCircle(
            target = drawTarget ?: return,
            cx = centerX,
            cy = centerY,
            radius = radius,
            mask = mask,
            color = color,
            mode = pixelMode,
        )
    }
}
