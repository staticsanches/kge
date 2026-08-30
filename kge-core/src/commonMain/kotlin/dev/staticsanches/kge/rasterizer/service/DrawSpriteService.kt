package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import kotlin.math.max
import kotlin.math.min

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

        val minX = min(diagonalStartX, diagonalEndX)
        val maxX = max(diagonalStartX, diagonalEndX)
        val minY = min(diagonalStartY, diagonalEndY)
        val maxY = max(diagonalStartY, diagonalEndY)

        val spriteWidth = maxX - minX + 1
        val spriteHeight = maxY - minY + 1

        val targetWidth = target.width
        val targetHeight = target.height
        if (x >= targetWidth || x + spriteWidth <= 0 || y >= targetHeight || y + spriteHeight <= 0) {
            return // out of bounds
        }

        val xSpriteStart = if (flip == Sprite.Flip.HORIZONTAL || flip == Sprite.Flip.BOTH) maxX else minX
        val xSpriteIncrement = if (flip == Sprite.Flip.HORIZONTAL || flip == Sprite.Flip.BOTH) -1 else 1
        val ySpriteStart = if (flip == Sprite.Flip.VERTICAL || flip == Sprite.Flip.BOTH) maxY else minY
        val ySpriteIncrement = if (flip == Sprite.Flip.VERTICAL || flip == Sprite.Flip.BOTH) -1 else 1

        if (scale == 1 && flip == Sprite.Flip.NONE && pixelMode == Pixel.Mode.Normal) {
            // Fast path: copies whole rows of the source into the target
            val destX = max(x, 0)
            val sourceOffsetX = destX - x
            val copyWidth = min(spriteWidth - sourceOffsetX, targetWidth - destX)
            for (j in 0..<spriteHeight) {
                val targetY = y + j
                if (targetY !in 0..<targetHeight) continue // out of bounds
                Rasterizer.copySpan(
                    destX, targetY,
                    target, pixelMode,
                    sprite, minX + sourceOffsetX, minY + j, copyWidth,
                )
            }
            return
        }

        if (scale > 1 && pixelMode == Pixel.Mode.Normal) {
            // Fast path: reads the source without bounds checks and draws each row of the
            // scaled block as a span
            for (j in 0..<spriteHeight) {
                val baseTargetY = y + j * scale
                if (baseTargetY !in 0..<targetHeight) continue // out of bounds

                val ySprite = ySpriteStart + j * ySpriteIncrement
                for (i in 0..<spriteWidth) {
                    val baseTargetX = x + i * scale
                    if (baseTargetX !in 0..<targetWidth) continue // out of bounds

                    val color = sprite.uncheckedGet(xSpriteStart + i * xSpriteIncrement, ySprite)
                    val spanStart = max(baseTargetX, 0)
                    val spanEnd = min(baseTargetX + scale - 1, targetWidth - 1)
                    for (jScale in 0..<scale) {
                        val targetY = baseTargetY + jScale
                        if (targetY !in 0..<targetHeight) continue // out of bounds
                        Rasterizer.drawSpan(spanStart, spanEnd, targetY, color, target, pixelMode)
                    }
                }
            }
            return
        }

        // Fallback: per-pixel drawing, iterating rows first so the target is written sequentially.
        if (scale == 1) {
            for (j in 0..<spriteHeight) {
                val targetY = y + j
                if (targetY !in 0..<targetHeight) continue // out of bounds

                val ySprite = ySpriteStart + j * ySpriteIncrement
                var xSprite = xSpriteStart
                for (i in 0..<spriteWidth) {
                    val targetX = x + i
                    if (targetX in 0..<targetWidth) {
                        Rasterizer.draw(targetX, targetY, sprite[xSprite, ySprite], target, pixelMode)
                    }
                    xSprite += xSpriteIncrement
                }
            }
        } else {
            for (j in 0..<spriteHeight) {
                val baseTargetY = y + j * scale
                if (baseTargetY !in 0..<targetHeight) continue // out of bounds

                val ySprite = ySpriteStart + j * ySpriteIncrement
                for (i in 0..<spriteWidth) {
                    val baseTargetX = x + i * scale
                    if (baseTargetX !in 0..<targetWidth) continue // out of bounds

                    val color = sprite[xSpriteStart + i * xSpriteIncrement, ySprite]
                    for (iScale in 0..<scale) {
                        val targetX = baseTargetX + iScale
                        if (targetX !in 0..<targetWidth) continue // out of bounds

                        for (jScale in 0..<scale) {
                            val targetY = baseTargetY + jScale
                            if (targetY !in 0..<targetHeight) continue // out of bounds
                            Rasterizer.draw(targetX, targetY, color, target, pixelMode)
                        }
                    }
                }
            }
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
