package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface DrawSpriteService : KGEExtensibleService {
    fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawPartialSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    companion object : DrawSpriteService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawSpriteServiceImplementation
}

val originalDrawSpriteServiceImplementation: DrawSpriteService
    get() = DefaultDrawSpriteService

private data object DefaultDrawSpriteService : DrawSpriteService {
    override fun drawSprite(
        position: Int2D,
        sprite: Sprite,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawSprite(
        x = position.x, y = position.y,
        sprite = sprite,
        scale = scale,
        flip = flip,
        target = target,
        pixelMode = pixelMode,
    )

    override fun drawSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawPartialSprite(
        x = x, y = y,
        sprite = sprite,
        diagonalStartX = 0, diagonalStartY = 0,
        diagonalEndX = sprite.width - 1, diagonalEndY = sprite.height - 1,
        scale = scale,
        flip = flip,
        target = target,
        pixelMode = pixelMode,
    )

    override fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawPartialSprite(
        x = position.x, y = position.y,
        sprite = sprite,
        diagonalStartX = diagonalStart.x, diagonalStartY = diagonalStart.y,
        diagonalEndX = diagonalEnd.x, diagonalEndY = diagonalEnd.y,
        scale = scale,
        flip = flip,
        target = target,
        pixelMode = pixelMode,
    )

    override fun drawPartialSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        scale: Int,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (scale <= 0) return // invalid scale

        val minX: Int
        val maxX: Int
        if (diagonalStartX <= diagonalEndX) {
            minX = diagonalStartX
            maxX = diagonalEndX
        } else {
            minX = diagonalEndX
            maxX = diagonalStartX
        }

        val minY: Int
        val maxY: Int
        if (diagonalStartY <= diagonalEndY) {
            minY = diagonalStartY
            maxY = diagonalEndY
        } else {
            minY = diagonalEndY
            maxY = diagonalStartY
        }

        val spriteWidth = maxX - minX + 1
        val spriteHeight = maxY - minY + 1

        val targetWidth = target.width
        val targetHeight = target.height
        if (x >= targetWidth || x + spriteWidth <= 0 || y >= targetHeight || y + spriteHeight <= 0) {
            return // out of bounds
        }

        val xSpriteStart: Int
        val xSpriteIncrement: Int
        if (flip == Sprite.Flip.HORIZONTAL || flip == Sprite.Flip.BOTH) {
            xSpriteStart = maxX
            xSpriteIncrement = -1
        } else {
            xSpriteStart = minX
            xSpriteIncrement = 1
        }

        val ySpriteStart: Int
        val ySpriteIncrement: Int
        if (flip == Sprite.Flip.VERTICAL || flip == Sprite.Flip.BOTH) {
            ySpriteStart = maxY
            ySpriteIncrement = -1
        } else {
            ySpriteStart = minY
            ySpriteIncrement = 1
        }

        if (scale == 1) {
            var xSprite = xSpriteStart
            for (i in 0..<spriteWidth) {
                val targetX = x + i
                if (targetX !in 0..<targetWidth) continue // out of bounds

                var ySprite = ySpriteStart
                for (j in 0..<spriteHeight) {
                    val targetY = y + j
                    if (targetY !in 0..<targetHeight) continue // out of bounds

                    Rasterizer.draw(targetX, targetY, sprite[xSprite, ySprite], target, pixelMode)

                    ySprite += ySpriteIncrement
                }
                xSprite += xSpriteIncrement
            }
        } else {
            var xSprite = xSpriteStart
            for (i in 0..<spriteWidth) {
                val baseTargetX = x + (i * scale)
                if (baseTargetX !in 0..<targetWidth) continue // out of bounds

                var ySprite = ySpriteStart
                for (j in 0..<spriteHeight) {
                    val baseTargetY = y + (j * scale)
                    if (baseTargetY !in 0..<targetHeight) continue // out of bounds

                    val color = sprite[xSprite, ySprite]

                    for (iScale in 0..<scale) {
                        val targetX = baseTargetX + iScale
                        if (targetX !in 0..<targetWidth) continue // out of bounds

                        for (jScale in 0..<scale) {
                            Rasterizer.draw(targetX, baseTargetY + jScale, color, target, pixelMode)
                        }
                    }

                    ySprite += ySpriteIncrement
                }
                xSprite += xSpriteIncrement
            }
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
