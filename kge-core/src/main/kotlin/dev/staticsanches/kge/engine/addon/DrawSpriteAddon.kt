package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawSpriteAddon {
    context(Window)
    fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: UInt = 1u,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawSprite(position, sprite, scale, flip, drawTarget ?: return, pixelMode)
    }

    context(Window)
    fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: UInt = 1u,
        flip: Sprite.Flip = Sprite.Flip.NONE,
    ) {
        Rasterizer.drawSprite(x, y, sprite, scale, flip, drawTarget ?: return, pixelMode)
    }
}
