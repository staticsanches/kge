package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel

interface ClearService : KGEExtensibleService {
    fun clear(
        pixel: Pixel,
        target: MutablePixelMap,
    )

    fun clear(
        pixelByXY: (x: Int, y: Int) -> Pixel,
        target: MutablePixelMap,
    )

    fun clear(
        pixels: Iterable<Pixel>,
        target: MutablePixelMap,
    )

    companion object : ClearService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalClearServiceImplementation
}

val originalClearServiceImplementation: ClearService
    get() = DefaultClearService

private data object DefaultClearService : ClearService {
    override fun clear(
        pixel: Pixel,
        target: MutablePixelMap,
    ) = target.clear(pixel)

    override fun clear(
        pixelByXY: (x: Int, y: Int) -> Pixel,
        target: MutablePixelMap,
    ) = target.clear(pixelByXY)

    override fun clear(
        pixels: Iterable<Pixel>,
        target: MutablePixelMap,
    ) = target.clear(pixels)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
