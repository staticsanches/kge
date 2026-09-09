package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.buffer.copyInts
import dev.staticsanches.kge.image.MutablePixmap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer

/**
 * The sprite blit family. [drawSprite] paints each source pixel as a
 * `scale x scale` block, resolving [Pixel.Mode] through [DrawService.draw]
 * via the [Rasterizer] aggregate; a whole-blit raw-row fast path
 * writes the rows directly on a [Sprite] target.
 */
interface DrawSpriteService : KGEOverridable {
    /**
     * Blits [sprite] so its top-left corner lands at ([x], [y]), painting the
     * pixel at source column/row (`c`, `r`) at `(x + (c * scale), y +
     * (r * scale))` as a `scale x scale` block. [flip] mirrors the read axes
     * inside the same footprint; a non-positive [scale] paints nothing. Each
     * source pixel resolves [mode] against the stored pixel through the draw
     * seam, so out-of-bounds cells of the destination are dropped.
     */
    fun drawSprite(
        target: MutablePixmap,
        x: Int,
        y: Int,
        sprite: Sprite,
        scale: Int,
        flip: Sprite.Flip,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [drawSprite] — unpacks the position to the raw method. */
    fun drawSprite(
        target: MutablePixmap,
        position: Int2D,
        sprite: Sprite,
        scale: Int,
        flip: Sprite.Flip,
        mode: Pixel.Mode,
    ): Unit = drawSprite(target, position.x, position.y, sprite, scale, flip, mode)

    companion object :
        KGEOverridable.Proxy<DrawSpriteService>(DrawSpriteService::class, drawSpriteServiceDefault),
        DrawSpriteService {
        override fun drawSprite(
            target: MutablePixmap,
            x: Int,
            y: Int,
            sprite: Sprite,
            scale: Int,
            flip: Sprite.Flip,
            mode: Pixel.Mode,
        ) = delegate.drawSprite(target, x, y, sprite, scale, flip, mode)

        override fun drawSprite(
            target: MutablePixmap,
            position: Int2D,
            sprite: Sprite,
            scale: Int,
            flip: Sprite.Flip,
            mode: Pixel.Mode,
        ) = delegate.drawSprite(target, position, sprite, scale, flip, mode)
    }
}

/** The platform-independent default — the blit is pure CPU over the surface accessors. */
private val drawSpriteServiceDefault: DrawSpriteService =
    object : DrawSpriteService {
        override fun drawSprite(
            target: MutablePixmap,
            x: Int,
            y: Int,
            sprite: Sprite,
            scale: Int,
            flip: Sprite.Flip,
            mode: Pixel.Mode,
        ) {
            if (scale <= 0) return

            val sWidth = sprite.width
            val sHeight = sprite.height
            val footprintW = sWidth * scale
            val footprintH = sHeight * scale
            if (x >= target.width || x + footprintW <= 0 || y >= target.height || y + footprintH <= 0) return

            val flipH = flip == Sprite.Flip.HORIZONTAL || flip == Sprite.Flip.BOTH
            val flipV = flip == Sprite.Flip.VERTICAL || flip == Sprite.Flip.BOTH

            if (
                target is Sprite &&
                mode == Pixel.Mode.Normal &&
                scale == 1 &&
                !flipH &&
                x >= 0 &&
                y >= 0 &&
                x + sWidth <= target.width &&
                y + sHeight <= target.height
            ) {
                // whole rows are copied raw: NORMAL ignores alpha, so the
                // copied ints match the draw seam verbatim write; a vertical
                // flip only swaps which source row lands on each target row.
                val dst = target.byteBuffer
                val src = sprite.byteBuffer
                for (j in 0 until sHeight) {
                    val srcRow = if (flipV) sHeight - 1 - j else j
                    dst.copyInts(
                        ((y + j) * target.width + x) * Int.SIZE_BYTES,
                        src,
                        srcRow * sWidth * Int.SIZE_BYTES,
                        sWidth,
                    )
                }
                return
            }

            for (j in 0 until sHeight) {
                val sy = if (flipV) sHeight - 1 - j else j
                for (i in 0 until sWidth) {
                    val sx = if (flipH) sWidth - 1 - i else i
                    val color = sprite.uncheckedGet(sx, sy)
                    for (blockY in 0 until scale) {
                        for (blockX in 0 until scale) {
                            DrawService.draw(target, x + i * scale + blockX, y + j * scale + blockY, color, mode)
                        }
                    }
                }
            }
        }
    }
