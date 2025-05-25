package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawLineService.Pattern

interface DrawRectService : KGEExtensibleService {
    fun drawRect(
        position: Int2D,
        size: Int2D,
        color: Pixel,
        pattern: Pattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Pixel,
        pattern: Pattern,
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
        position: Int2D,
        size: Int2D,
        color: Pixel,
        pattern: Pattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawRect(
        x = position.x,
        y = position.y,
        width = size.x,
        height = size.y,
        color = color,
        pattern = pattern,
        target = target,
        pixelMode = pixelMode,
    )

    override fun drawRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Pixel,
        pattern: Pattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        Rasterizer.drawLine(x, y, x + width, y, color, pattern, target, pixelMode) // top
        Rasterizer.drawLine(x + width, y, x + width, y + height, color, pattern, target, pixelMode) // right
        Rasterizer.drawLine(x + width, y + height, x, y + height, color, pattern, target, pixelMode) // bottom
        Rasterizer.drawLine(x, y + height, x, y, color, pattern, target, pixelMode) // left
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
