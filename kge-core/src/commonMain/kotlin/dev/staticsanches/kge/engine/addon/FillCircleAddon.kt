package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.CircleOctantMask
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Fills circles into the draw target, a no-op when it is null. */
interface FillCircleAddon :
    HasDrawTarget,
    HasDrawModes {
    /**
     * Fills the [mask] octants of the circle of [radius] around [center] in
     * [color].
     */
    fun fillCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillCircle(
            target = drawTarget ?: return,
            center = center,
            radius = radius,
            mask = mask,
            color = color,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [fillCircle]. */
    fun fillCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask = CircleOctantMask.ALL,
        color: Pixel = Colors.WHITE,
    ) {
        Rasterizer.fillCircle(
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
