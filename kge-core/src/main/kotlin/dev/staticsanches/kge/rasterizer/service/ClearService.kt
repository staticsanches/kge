package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.MutablePixelMap
import dev.staticsanches.kge.spi.KGESPIExtensible

interface ClearService : KGESPIExtensible {
    fun clear(
        pixel: Pixel,
        target: MutablePixelMap,
    )

    fun clear(
        pixelByXY: (x: Int, y: Int) -> Pixel,
        target: MutablePixelMap,
    )
}

internal object DefaultClearService : ClearService {
    override fun clear(
        pixel: Pixel,
        target: MutablePixelMap,
    ) = target.clear(pixel)

    override fun clear(
        pixelByXY: (x: Int, y: Int) -> Pixel,
        target: MutablePixelMap,
    ) = target.clear(pixelByXY)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
