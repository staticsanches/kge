package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.spi.KGESPIExtensible

interface ClearService : KGESPIExtensible {
    fun clear(
        pixel: Pixel,
        target: PixelMap,
    )

    fun clear(
        pixelByXY: (x: Int, y: Int) -> Pixel,
        target: PixelMap,
    )
}

internal object DefaultClearService : ClearService {
    override fun clear(
        pixel: Pixel,
        target: PixelMap,
    ) = target.clear(pixel)

    override fun clear(
        pixelByXY: (x: Int, y: Int) -> Pixel,
        target: PixelMap,
    ) = target.clear(pixelByXY)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
