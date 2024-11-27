package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawRectService : KGESPIExtensible {
    fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawRectService : DrawRectService {
    override fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawRect(diagonalStart.x, diagonalStart.y, diagonalEnd.x, diagonalEnd.y, color, target, pixelMode)

    override fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        // Top edge
        Rasterizer.drawLine(diagonalStartX, diagonalStartY, diagonalEndX, diagonalStartY, color, target, pixelMode)
        // Bottom edge
        Rasterizer.drawLine(diagonalStartX, diagonalEndY, diagonalEndX, diagonalEndY, color, target, pixelMode)
        // Left edge
        Rasterizer.drawLine(diagonalStartX, diagonalStartY, diagonalStartX, diagonalEndY, color, target, pixelMode)
        // Right edge
        Rasterizer.drawLine(diagonalEndX, diagonalStartY, diagonalEndX, diagonalEndY, color, target, pixelMode)
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
