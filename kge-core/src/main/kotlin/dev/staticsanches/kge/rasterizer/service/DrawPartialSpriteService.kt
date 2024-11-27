package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawPartialSpriteService : KGESPIExtensible {
    fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: UInt,
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
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawPartialSpriteService : DrawPartialSpriteService {
    override fun drawPartialSprite(
        position: Int2D,
        sprite: Sprite,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawPartialSprite(
        position.x,
        position.y,
        sprite,
        diagonalStart.x,
        diagonalStart.y,
        diagonalEnd.x,
        diagonalEnd.y,
        scale,
        flip,
        target,
        pixelMode,
    )

    override fun drawPartialSprite(
        x: Int,
        y: Int,
        sprite: Sprite,
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        scale: UInt,
        flip: Sprite.Flip,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (scale == 0u) return // invalid scale

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

        if (scale == 1u) {
            var xSprite = xSpriteStart
            for (i in 0..<spriteWidth) {
                val targetX = x + i
                if (targetX !in 0..<targetWidth) continue // out of bounds

                var ySprite = ySpriteStart
                for (j in 0..<spriteHeight) {
                    val targetY = y + j
                    if (targetY !in 0..<targetHeight) continue // out of bounds

                    Rasterizer.draw(
                        targetX,
                        targetY,
                        sprite[xSprite, ySprite],
                        target,
                        pixelMode,
                    )

                    ySprite += ySpriteIncrement
                }
                xSprite += xSpriteIncrement
            }
        } else {
            val intScale = scale.toInt()
            var xSprite = xSpriteStart
            for (i in 0..<spriteWidth) {
                val baseTargetX = x + (i * intScale)
                if (baseTargetX !in 0..<targetWidth) continue // out of bounds

                var ySprite = ySpriteStart
                for (j in 0..<spriteHeight) {
                    val baseTargetY = y + (j * intScale)
                    if (baseTargetY !in 0..<targetHeight) continue // out of bounds

                    val color = sprite[xSprite, ySprite]

                    for (iScale in 0..<intScale) {
                        val targetX = baseTargetX + iScale
                        if (targetX !in 0..<targetWidth) continue // out of bounds

                        for (jScale in 0..<intScale) {
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
