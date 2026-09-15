package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

/** Draws single pixels into the draw target, a no-op when it is null. */
interface DrawAddon :
    HasDrawTarget,
    HasDrawModes {
    /** Draws [color] at [position]; `false` when the target is null or the point is outside. */
    fun draw(
        position: Int2D,
        color: Pixel = Colors.WHITE,
    ): Boolean = draw(position.x, position.y, color)

    /** Draws [color] at ([x], [y]); `false` when the target is null or the point is outside. */
    fun draw(
        x: Int,
        y: Int,
        color: Pixel = Colors.WHITE,
    ): Boolean =
        Rasterizer.draw(
            target = drawTarget ?: return false,
            x = x,
            y = y,
            color = color,
            mode = pixelMode,
        )
}
