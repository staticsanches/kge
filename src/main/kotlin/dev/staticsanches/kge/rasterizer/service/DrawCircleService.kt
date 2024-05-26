package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.CircleOctantMask
import dev.staticsanches.kge.rasterizer.utils.bresenhamCircle
import dev.staticsanches.kge.spi.KGESPIExtensible

interface DrawCircleService : KGESPIExtensible {
    fun drawCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultDrawCircleService : DrawCircleService {
    override fun drawCircle(
        center: Int2D,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) = drawCircle(center.x, center.y, radius, mask, color, target, pixelMode)

    override fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        mask: CircleOctantMask,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (
            radius < 0 ||
            mask == CircleOctantMask.NONE ||
            centerX < -radius || centerX - target.width > radius ||
            centerY < -radius || centerY - target.height > radius
        ) {
            return
        }

        if (radius == 0) { // only the center was requested
            Rasterizer.draw(centerX, centerY, color, target, pixelMode)
            return
        }

        bresenhamCircle(radius, mask) { x, y, o1, o2, o3, o4, o5, o6, o7, o8 ->
            if (o1) Rasterizer.draw(centerX + x, centerY - y, color, target, pixelMode)
            if (o3) Rasterizer.draw(centerX + y, centerY + x, color, target, pixelMode)
            if (o5) Rasterizer.draw(centerX - x, centerY + y, color, target, pixelMode)
            if (o7) Rasterizer.draw(centerX - y, centerY - x, color, target, pixelMode)

            if (x != 0 && x != y) {
                if (o2) Rasterizer.draw(centerX + y, centerY - x, color, target, pixelMode)
                if (o4) Rasterizer.draw(centerX + x, centerY + y, color, target, pixelMode)
                if (o6) Rasterizer.draw(centerX - y, centerY + x, color, target, pixelMode)
                if (o8) Rasterizer.draw(centerX - x, centerY - y, color, target, pixelMode)
            }
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
