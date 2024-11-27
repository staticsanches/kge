package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.fittestX
import dev.staticsanches.kge.rasterizer.fittestY
import dev.staticsanches.kge.spi.KGESPIExtensible

interface FillRectService : KGESPIExtensible {
    fun fillRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun fillRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultFillRectService : FillRectService {
    override fun fillRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = fillRect(diagonalStart.x, diagonalStart.y, diagonalEnd.x, diagonalEnd.y, color, target, pixelMode)

    override fun fillRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        var x0 = diagonalStartX
        var y0 = diagonalStartY
        var x1 = diagonalEndX
        var y1 = diagonalEndY

        // Ensures x0 <= x1 and y0 <= y1
        if (x0 > x1) x0 = x1.also { x1 = x0 }
        if (y0 > y1) y0 = y1.also { y1 = y0 }

        if (x1 < 0 || y1 < 0 || x0 >= target.width || y0 >= target.height) return // outside viewport

        // Clip to target
        x0 = target.fittestX(x0)
        y0 = target.fittestY(y0)
        x1 = target.fittestX(x1)
        y1 = target.fittestY(y1)

        for (x in x0..x1) {
            for (y in y0..y1) {
                Rasterizer.draw(x, y, color, target, pixelMode)
            }
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
