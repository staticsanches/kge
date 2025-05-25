package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawLineService.LinePattern

interface DrawRectService : KGEExtensibleService {
    fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    companion object : DrawRectService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawRectServiceImplementation
}

private val originalDrawRectServiceImplementation: DrawRectService
    get() = DefaultDrawRectService

private data object DefaultDrawRectService : DrawRectService {
    override fun drawRect(
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawRect(
        diagonalStartX = diagonalStart.x,
        diagonalStartY = diagonalStart.y,
        diagonalEndX = diagonalEnd.x,
        diagonalEndY = diagonalEnd.y,
        color = color,
        pattern = pattern,
        target = target,
        pixelMode = pixelMode,
    )

    override fun drawRect(
        diagonalStartX: Int,
        diagonalStartY: Int,
        diagonalEndX: Int,
        diagonalEndY: Int,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        // Top edge
        Rasterizer.drawLine(
            startX = diagonalStartX, startY = diagonalStartY,
            endX = diagonalEndX, endY = diagonalStartY,
            color = color, pattern = pattern, target = target, pixelMode = pixelMode,
        )
        // Right edge
        Rasterizer.drawLine(
            startX = diagonalEndX, startY = diagonalStartY,
            endX = diagonalEndX, endY = diagonalEndY,
            color = color, pattern = pattern, target = target, pixelMode = pixelMode,
        )
        // Bottom edge
        Rasterizer.drawLine(
            startX = diagonalEndX, startY = diagonalEndY,
            endX = diagonalStartX, endY = diagonalEndY,
            color = color, pattern = pattern, target = target, pixelMode = pixelMode,
        )
        // Left edge
        Rasterizer.drawLine(
            startX = diagonalStartX, startY = diagonalEndY,
            endX = diagonalStartX, endY = diagonalStartY,
            color = color, pattern = pattern, target = target, pixelMode = pixelMode,
        )
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
