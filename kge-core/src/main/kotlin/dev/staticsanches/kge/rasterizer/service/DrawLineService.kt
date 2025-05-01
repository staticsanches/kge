package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.BresenhamLine
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawLineService : KGESPIExtensible {
    fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawLineService : DrawLineService {
    override fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = BresenhamLine(start, end, target).forEach { Rasterizer.draw(it, color, target, pixelMode) }

    override fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawLine(startX by startY, endX by endY, color, target, pixelMode)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
