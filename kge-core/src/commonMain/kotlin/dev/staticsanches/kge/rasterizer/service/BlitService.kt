package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.copyInts
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer

/**
 * The blit family: [blit] paints a whole [Pixmap] source, [blitRegion] a
 * sub-rectangle of one. Each source pixel lands as a `scale x scale` block,
 * resolving [Pixel.Mode] through [DrawService.draw] via the [Rasterizer]
 * aggregate; a raw-backed source and target additionally enable a whole-row
 * raw-copy fast path under Normal at scale 1 without a horizontal flip.
 */
interface BlitService : KGEOverridable {
    /**
     * Blits [source] so its top-left corner lands at ([x], [y]), painting the
     * pixel at source column/row (`c`, `r`) at `(x + (c * scale), y +
     * (r * scale))` as a `scale x scale` block. [flip] mirrors the read axes
     * inside the same footprint; a non-positive [scale] paints nothing. Each
     * source pixel resolves [mode] through the draw seam, so out-of-bounds
     * cells of the destination are dropped.
     */
    fun blit(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        source: Pixmap,
        scale: Int,
        flip: Pixmap.Flip,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [blit] — unpacks the position to the raw method. */
    fun blit(
        target: Pixmap.Mutable,
        position: Int2D,
        source: Pixmap,
        scale: Int,
        flip: Pixmap.Flip,
        mode: Pixel.Mode,
    ): Unit = blit(target, position.x, position.y, source, scale, flip, mode)

    /**
     * Blits the [size]-sized region of [source] at [origin] so its top-left
     * lands at ([x], [y]), with the same footprint, flip and mode rules as
     * [blit]. The region must be non-empty and inside [source], else
     * [IllegalArgumentException]; any destination clip is the ordinary
     * footprint clip.
     */
    fun blitRegion(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        source: Pixmap,
        origin: Int2D,
        size: Int2D,
        scale: Int,
        flip: Pixmap.Flip,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [blitRegion] — unpacks the position to the raw method. */
    fun blitRegion(
        target: Pixmap.Mutable,
        position: Int2D,
        source: Pixmap,
        origin: Int2D,
        size: Int2D,
        scale: Int,
        flip: Pixmap.Flip,
        mode: Pixel.Mode,
    ): Unit = blitRegion(target, position.x, position.y, source, origin, size, scale, flip, mode)

    companion object :
        KGEOverridable.Proxy<BlitService>(BlitService::class, blitServiceDefault),
        BlitService {
        override fun blit(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) = delegate.blit(target, x, y, source, scale, flip, mode)

        override fun blit(
            target: Pixmap.Mutable,
            position: Int2D,
            source: Pixmap,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) = delegate.blit(target, position, source, scale, flip, mode)

        override fun blitRegion(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
            origin: Int2D,
            size: Int2D,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) = delegate.blitRegion(target, x, y, source, origin, size, scale, flip, mode)

        override fun blitRegion(
            target: Pixmap.Mutable,
            position: Int2D,
            source: Pixmap,
            origin: Int2D,
            size: Int2D,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) = delegate.blitRegion(target, position, source, origin, size, scale, flip, mode)
    }
}

/** The platform-independent default — the blit is pure CPU over the surface accessors. */
@OptIn(KGESensitiveAPI::class)
private val blitServiceDefault: BlitService =
    object : BlitService {
        override fun blit(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) = blitCore(target, x, y, source, 0, 0, source.width, source.height, scale, flip, mode)

        override fun blitRegion(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
            origin: Int2D,
            size: Int2D,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) {
            require(size.x > 0 && size.y > 0) { "region size must be positive: $size" }
            require(origin.x >= 0 && origin.y >= 0) { "region origin must be non-negative: $origin" }
            require(origin.x + size.x <= source.width && origin.y + size.y <= source.height) {
                "region origin $origin size $size is outside ${source.width}x${source.height}"
            }
            blitCore(target, x, y, source, origin.x, origin.y, size.x, size.y, scale, flip, mode)
        }

        private fun blitCore(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
            sx: Int,
            sy: Int,
            w: Int,
            h: Int,
            scale: Int,
            flip: Pixmap.Flip,
            mode: Pixel.Mode,
        ) {
            if (scale <= 0) return

            val footprintW = w * scale
            val footprintH = h * scale
            if (x >= target.width || x + footprintW <= 0 || y >= target.height || y + footprintH <= 0) return

            val flipH = flip == Pixmap.Flip.HORIZONTAL || flip == Pixmap.Flip.BOTH
            val flipV = flip == Pixmap.Flip.VERTICAL || flip == Pixmap.Flip.BOTH

            if (
                source is Pixmap.RawBacked &&
                target is Pixmap.RawBacked &&
                mode == Pixel.Mode.Normal &&
                scale == 1 &&
                !flipH &&
                x >= 0 &&
                y >= 0 &&
                x + w <= target.width &&
                y + h <= target.height
            ) {
                // whole rows are copied raw: NORMAL ignores alpha, so the
                // copied ints match the draw seam verbatim write; a vertical
                // flip only swaps which source row lands on each target row.
                copyRows(target, source, x, y, sx, sy, w, h, flipV)
                return
            }

            for (j in 0 until h) {
                val syLocal = if (flipV) h - 1 - j else j
                for (i in 0 until w) {
                    val sxLocal = if (flipH) w - 1 - i else i
                    val color = source.uncheckedGet(sx + sxLocal, sy + syLocal)
                    for (blockY in 0 until scale) {
                        for (blockX in 0 until scale) {
                            DrawService.draw(target, x + i * scale + blockX, y + j * scale + blockY, color, mode)
                        }
                    }
                }
            }
        }

        private fun copyRows(
            dst: Pixmap.RawBacked,
            src: Pixmap.RawBacked,
            x: Int,
            y: Int,
            sx: Int,
            sy: Int,
            w: Int,
            h: Int,
            flipV: Boolean,
        ) {
            val dstBuffer = dst.buffer
            val srcBuffer = src.buffer
            // when the rectangle fills whole rows in both buffers the block is
            // one contiguous int run on each side, so a single bulk copy covers
            // it instead of one copy per row
            if (!flipV && sx == 0 && x == 0 && w == src.stride && w == dst.stride) {
                dstBuffer.copyInts(
                    dst.index(0, y) * Int.SIZE_BYTES,
                    srcBuffer,
                    src.index(0, sy) * Int.SIZE_BYTES,
                    w * h,
                )
                return
            }
            for (j in 0 until h) {
                val srcRow = if (flipV) sy + h - 1 - j else sy + j
                dstBuffer.copyInts(
                    dst.index(x, y + j) * Int.SIZE_BYTES,
                    srcBuffer,
                    src.index(sx, srcRow) * Int.SIZE_BYTES,
                    w,
                )
            }
        }
    }
