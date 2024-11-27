package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.CircleOctantMask
import dev.staticsanches.kge.rasterizer.utils.bresenhamCircle
import dev.staticsanches.kge.spi.KGESPIExtensible

interface FillCircleService : KGESPIExtensible {
    fun fillCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun fillCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultFillCircleService : FillCircleService {
    override fun fillCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = fillCircle(center.x, center.y, radius, mask, color, target, pixelMode)

    override fun fillCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (
            radius < 0 ||
            mask == CircleOctantMask.NONE ||
            centerX < -radius ||
            centerX - target.width > radius ||
            centerY < -radius ||
            centerY - target.height > radius
        ) {
            return
        }

        if (radius == 0) { // only the center was requested
            Rasterizer.draw(centerX, centerY, color, target, pixelMode)
            return
        }

        bresenhamCircle(radius, mask) { x, y, o1, o2, o3, o4, o5, o6, o7, o8 ->
            for (j in x..<y) {
                if (o1) Rasterizer.draw(centerX + x, centerY - j, color, target, pixelMode)
                if (o2) Rasterizer.draw(centerX + j, centerY - x, color, target, pixelMode)
                if (o3) Rasterizer.draw(centerX + j, centerY + x, color, target, pixelMode)
                if (o4) Rasterizer.draw(centerX + x, centerY + j, color, target, pixelMode)
                if (o5) Rasterizer.draw(centerX - x, centerY + j, color, target, pixelMode)
                if (o6) Rasterizer.draw(centerX - j, centerY + x, color, target, pixelMode)
                if (o7) Rasterizer.draw(centerX - j, centerY - x, color, target, pixelMode)
                if (o8) Rasterizer.draw(centerX - x, centerY - j, color, target, pixelMode)
            }
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
