@file:Suppress("unused")

package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawSpriteAddon : WindowDependentAddon {
    fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: Int = 1,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawSprite(
            position = position,
            sprite = sprite,
            scale = scale,
            flip = flip,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: Int = 1,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawSprite(
            x = x,
            y = y,
            sprite = sprite,
            scale = scale,
            flip = flip,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }

    fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: Int = 1,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawPartialSprite(
            position = position,
            sprite = sprite,
            diagonalStart = diagonalStart,
            diagonalEnd = diagonalEnd,
            scale = scale,
            flip = flip,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
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
        scale: Int = 1,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawPartialSprite(
            x = x,
            y = y,
            sprite = sprite,
            diagonalStartX = diagonalStartX,
            diagonalStartY = diagonalStartY,
            diagonalEndX = diagonalEndX,
            diagonalEndY = diagonalEndY,
            scale = scale,
            flip = flip,
            target = drawTarget ?: return,
            pixelMode = pixelMode,
        )
    }
}
