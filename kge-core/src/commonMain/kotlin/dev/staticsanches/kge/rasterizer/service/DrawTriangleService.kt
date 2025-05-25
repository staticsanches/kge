package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.service.DrawLineService.LinePattern
import dev.staticsanches.kge.rasterizer.utils.SortedTriangleVertices

interface DrawTriangleService : KGEExtensibleService {
    fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        pattern: LinePattern,
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
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    companion object : DrawTriangleService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawTriangleServiceImplementation
}

val originalDrawTriangleServiceImplementation: DrawTriangleService
    get() = DefaultDrawTriangleService

private data object DefaultDrawTriangleService : DrawTriangleService {
    override fun drawTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = innerDrawTriangle(SortedTriangleVertices(p0, p1, p2), color, pattern, target, pixelMode)

    override fun drawTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = innerDrawTriangle(SortedTriangleVertices(x0 by y0, x1 by y1, x2 by y2), color, pattern, target, pixelMode)

    private fun innerDrawTriangle(
        vertices: SortedTriangleVertices,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        val (p0, p1, p2) = vertices
        if (vertices.areCollinear) {
            Rasterizer.drawLine(p0, p2, color, pattern, target, pixelMode)
        } else {
            Rasterizer.drawLine(p0, p1, color, pattern, target, pixelMode)
            Rasterizer.drawLine(p1, p2, color, pattern, target, pixelMode)
            Rasterizer.drawLine(p2, p0, color, pattern, target, pixelMode)
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
