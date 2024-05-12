package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.spi.KGESPIExtensible
import dev.staticsanches.kge.types.vector.Int2D

interface DrawService : KGESPIExtensible {
    fun draw(
        position: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean

    fun draw(
        x: Int,
        y: Int,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean
}

internal object DefaultDrawService : DrawService {
    override fun draw(
        position: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean = draw(position.x, position.y, color, target, pixelMode)

    override fun draw(
        x: Int,
        y: Int,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean =
        when (pixelMode) {
            Pixel.Mode.Normal -> target.set(x, y, color)
            Pixel.Mode.Mask -> color.a == 255 && target.set(x, y, color)
            is Pixel.Mode.Custom -> target.set(x, y, pixelMode(x, y, color, target.uncheckedGet(x, y)))
            is Pixel.Mode.Alpha -> target.set(x, y, target[x, y].lerp(color, color.a * pixelMode.blendFactor / 255f))
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
