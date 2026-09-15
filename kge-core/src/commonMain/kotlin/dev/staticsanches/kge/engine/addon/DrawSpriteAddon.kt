package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import kotlin.math.abs

/** Blits sprites into the draw target, a no-op when it is null. */
interface DrawSpriteAddon :
    HasDrawTarget,
    HasDrawModes {
    /** Blits the whole [sprite] with its top-left at [position]. */
    fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: Int = 1,
        flip: Pixmap.Flip = Pixmap.Flip.NONE,
    ) {
        Rasterizer.blit(
            target = drawTarget ?: return,
            position = position,
            source = sprite,
            scale = scale,
            flip = flip,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [drawSprite]. */
    fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: Int = 1,
        flip: Pixmap.Flip = Pixmap.Flip.NONE,
    ) = drawSprite(Int2D(x, y), sprite, scale, flip)

    /**
     * Blits the inclusive source region between [diagonalStart] and
     * [diagonalEnd] with its top-left at [position]. The region must lie inside
     * [sprite], else [IllegalArgumentException].
     */
    fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: Int = 1,
        flip: Pixmap.Flip = Pixmap.Flip.NONE,
    ) {
        Rasterizer.blitRegion(
            target = drawTarget ?: return,
            position = position,
            source = sprite,
            origin = regionOrigin(diagonalStart, diagonalEnd),
            size = regionSize(diagonalStart, diagonalEnd),
            scale = scale,
            flip = flip,
            mode = pixelMode,
        )
    }

    /** The raw-`Int` form of [drawPartialSprite]. */
    fun drawPartialSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        scale: Int = 1,
        flip: Pixmap.Flip = Pixmap.Flip.NONE,
    ) = drawPartialSprite(
        Int2D(x, y),
        sprite,
        Int2D(diagonalStartX, diagonalStartY),
        Int2D(diagonalEndX, diagonalEndY),
        scale,
        flip,
    )
}

/** The min corner of the inclusive diagonal. */
private fun regionOrigin(
    start: Int2D,
    end: Int2D,
): Int2D = start.min(end)

/** The size of the inclusive diagonal region. */
private fun regionSize(
    start: Int2D,
    end: Int2D,
): Int2D = Int2D(abs(end.x - start.x) + 1, abs(end.y - start.y) + 1)
