package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D

interface DrawService : KGEExtensibleService {
    fun draw(
        position: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean

    fun draw(
        x: Int,
        y: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean

    companion object : DrawService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawServiceImplementation
}

val originalDrawServiceImplementation: DrawService
    get() = DefaultDrawService

private data object DefaultDrawService : DrawService {
    override fun draw(
        position: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean = draw(position.x, position.y, color, target, pixelMode)

    override fun draw(
        x: Int,
        y: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ): Boolean =
        when (pixelMode) {
            Pixel.Mode.Normal -> target.set(x, y, color)
            Pixel.Mode.Mask -> color.a == 255 && target.set(x, y, color)
            is Pixel.Mode.Custom -> target.setPixel(x, y, pixelMode.apply(x, y, color, target.getPixel(x, y)))
            is Pixel.Mode.Alpha -> {
                val a = (color.a / 255f) * pixelMode.blendFactor
                val c = 1f - a
                val old = target[x, y]
                val r = a * color.r + c * old.r
                val g = a * color.g + c * old.g
                val b = a * color.b + c * old.b
                target.set(x, y, Pixel.rgba(r, g, b))
            }
        }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
