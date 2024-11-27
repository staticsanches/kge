package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawSpriteService : KGESPIExtensible {
    fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawSpriteService : DrawSpriteService {
    override fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawSprite(
        position.x,
        position.y,
        sprite,
        scale,
        flip,
        target,
        pixelMode,
    )

    override fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = Rasterizer.drawPartialSprite(
        x,
        y,
        sprite,
        0,
        0,
        sprite.width - 1,
        sprite.height - 1,
        scale,
        flip,
        target,
        pixelMode,
    )

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
