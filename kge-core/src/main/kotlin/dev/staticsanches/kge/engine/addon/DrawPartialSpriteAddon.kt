package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawPartialSpriteAddon : WindowDependentAddon {
    fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: UInt = 1u,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawPartialSprite(
            position,
            sprite,
            diagonalStart,
            diagonalEnd,
            scale,
            flip,
            drawTarget ?: return,
            pixelMode,
        )
    }

    fun drawPartialSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        scale: UInt = 1u,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawPartialSprite(
            x,
            y,
            sprite,
            diagonalStartX,
            diagonalStartY,
            diagonalEndX,
            diagonalEndY,
            scale,
            flip,
            drawTarget ?: return,
            pixelMode,
        )
    }
}
