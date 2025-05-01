package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.SortedTriangleVertices
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawTriangleService : KGESPIExtensible {
    fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: MutablePixelMap,
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
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawTriangleService : DrawTriangleService {
    override fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = innerDrawTriangle(SortedTriangleVertices(p0, p1, p2), color, target, pixelMode)

    override fun drawTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = innerDrawTriangle(SortedTriangleVertices(x0 by y0, x1 by y1, x2 by y2), color, target, pixelMode)

    private fun innerDrawTriangle(
        vertices: SortedTriangleVertices,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        val (p0, p1, p2) = vertices
        if (vertices.areCollinear) {
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
