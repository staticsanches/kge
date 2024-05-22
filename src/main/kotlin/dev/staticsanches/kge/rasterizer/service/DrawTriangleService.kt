package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.MutableInt2D
import dev.staticsanches.kge.math.vector.mutableBy
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.areCollinear
import dev.staticsanches.kge.rasterizer.utils.sortTriangleVertices
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawTriangleService : KGESPIExtensible {
    fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawTriangleService : DrawTriangleService {
    override fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) = innerDrawTriangle(p0.toMutable(), p1.toMutable(), p2.toMutable(), color, target, pixelMode)

    override fun drawTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) = innerDrawTriangle(x0 mutableBy y0, x1 mutableBy y1, x2 mutableBy y2, color, target, pixelMode)

    private fun innerDrawTriangle(
        p0: MutableInt2D,
        p1: MutableInt2D,
        p2: MutableInt2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) {
        sortTriangleVertices(p0, p1, p2)

        if (areCollinear(p0, p1, p2)) {
            Rasterizer.drawLine(p0, p2, color, target, pixelMode)
        } else {
            Rasterizer.drawLine(p0, p1, color, target, pixelMode)
            Rasterizer.drawLine(p1, p2, color, target, pixelMode)
            Rasterizer.drawLine(p2, p0, color, target, pixelMode)
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
