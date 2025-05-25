package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer

interface FillRectService : KGEExtensibleService {
    fun fillRect(
        position: Int2D,
        size: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    companion object : FillRectService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalFillRectServiceImplementation
}

val originalFillRectServiceImplementation: FillRectService
    get() = DefaultFillRectService

private data object DefaultFillRectService : FillRectService {
    override fun fillRect(
        position: Int2D,
        size: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = fillRect(
        x = position.x,
        y = position.y,
        width = size.x,
        height = size.y,
        color = color,
        target = target,
        pixelMode = pixelMode,
    )

    override fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        var x1 = x
        var y1 = y
        if (x1 < 0) x1 = 0
        if (x1 > target.width) x1 = target.width
        if (y1 < 0) y1 = 0
        if (y1 > target.height) y1 = target.height

        var x2 = x + width
        var y2 = y + height
        if (x2 < 0) x2 = 0
        if (x2 > target.width) x2 = target.width
        if (y2 < 0) y2 = 0
        if (y2 > target.height) y2 = target.height

        for (i in x1..<x2) {
            for (j in y1..<y2) {
                Rasterizer.draw(i, j, color, target, pixelMode)
            }
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
