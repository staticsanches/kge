package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.MutableInt2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import kotlin.math.abs

interface DrawLineService : KGEExtensibleService {
    fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel,
        pattern: UInt,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel,
        pattern: UInt,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    companion object : DrawLineService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawLineServiceImplementation
}

val originalDrawLineServiceImplementation: DrawLineService
    get() = DefaultDrawLineService

private data object DefaultDrawLineService : DrawLineService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE

    override fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel,
        pattern: UInt,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawLine(
        startX = start.x, startY = start.y,
        endX = end.x, endY = end.y,
        color = color,
        pattern = pattern,
        target = target,
        pixelMode = pixelMode,
    )

    override fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel,
        pattern: UInt,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        var x1 = startX
        var y1 = startY
        var x2 = endX
        var y2 = endY
        val p1 = MutableInt2D(x1, y1)
        val p2 = MutableInt2D(x2, y2)

        if (!Rasterizer.clipLine(target, p1, p2)) return // outside target bounds

        val draw: (Int, Int) -> Unit =
            if (pattern == 0xFF_FF_FF_FFu) {
                { x, y -> Rasterizer.draw(x, y, color, target, pixelMode) }
            } else {
                var rol = pattern
                { x, y ->
                    rol = (rol shl 1) and (rol shr 31)
                    if (rol and 1u != 0u) Rasterizer.draw(x, y, color, target, pixelMode)
                }
            }

        val dx = x2 - x1
        val dy = y2 - y1

        x1 = p1.x
        y1 = p1.y
        x2 = p2.x
        y2 = p2.y

        if (dx == 0) { // Line is vertical
            if (y2 < y1) y1 = y2.also { y2 = y1 }
            for (y in y1..y2) draw(x1, y)
            return
        }

        if (dy == 0) { // Line is horizontal
            if (x2 < x1) x1 = x2.also { x2 = x1 }
            for (x in x1..x2) draw(x, y1)
            return
        }

        // Line is Funk-aye
        val dx1 = abs(dx)
        val dy1 = abs(dy)
        if (dy1 <= dx1) {
            var x: Int
            var y: Int
            val xe: Int
            if (dx >= 0) {
                x = x1
                y = y1
                xe = x2
            } else {
                x = x2
                y = y2
                xe = x1
            }
            draw(x, y)

            var px = 2 * dy1 - dx1
            while (x < xe) {
                x++
                if (px < 0) {
                    px = px + 2 * dy1
                } else {
                    if ((dx < 0 && dy < 0) || (dx > 0 && dy > 0)) {
                        y++
                    } else {
                        y--
                    }
                    px = px + 2 * (dy1 - dx1)
                }
                draw(x, y)
            }
        } else {
            var x: Int
            var y: Int
            val ye: Int
            if (dy >= 0) {
                x = x1
                y = y1
                ye = y2
            } else {
                x = x2
                y = y2
                ye = y1
            }
            draw(x, y)

            var py = 2 * dx1 - dy1
            while (y < ye) {
                y++
                if (py <= 0) {
                    py += 2 * dx1
                } else {
                    if ((dx < 0 && dy < 0) || (dx > 0 && dy > 0)) {
                        x++
                    } else {
                        x--
                    }
                    py += 2 * (dx1 - dy1)
                }
                draw(x, y)
            }
        }
    }
}
