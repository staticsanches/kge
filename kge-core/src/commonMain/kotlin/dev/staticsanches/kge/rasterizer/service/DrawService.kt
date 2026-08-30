package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.buffer.copyInts
import dev.staticsanches.kge.buffer.fillInts
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.RGBABuffer
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.utils.BytesSize.INT

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

    /**
     * Draws a contiguous horizontal span of pixels, from [startX] to [endX] (inclusive), on the [y] row.
     *
     * The default implementation draws each pixel via [draw]. Implementations may override it to
     * fill the whole span more efficiently (e.g. with direct buffer writes).
     */
    fun drawSpan(
        startX: Int,
        endX: Int,
        y: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        for (x in startX..endX) {
            draw(x, y, color, target, pixelMode)
        }
    }

    /**
     * Copies [length] contiguous pixels of the [source] sprite, starting at ([sourceX], [sourceY]),
     * into the [target] at ([startX], [y]) (the pixels of the row are copied left to right).
     *
     * The default implementation draws each pixel via [draw]. Implementations may override it to
     * copy the whole row more efficiently (e.g. with direct buffer reads/writes).
     */
    fun copySpan(
        startX: Int,
        y: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        source: Sprite,
        sourceX: Int,
        sourceY: Int,
        length: Int,
    ) {
        for (i in 0..<length) {
            draw(startX + i, y, source[sourceX + i, sourceY], target, pixelMode)
        }
    }

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
            Pixel.Mode.Normal -> {
                target.set(x, y, color)
            }

            Pixel.Mode.Mask -> {
                color.a == 255 && target.set(x, y, color)
            }

            is Pixel.Mode.Custom -> {
                target.setPixel(x, y, pixelMode.apply(x, y, color, target.getPixel(x, y)))
            }

            is Pixel.Mode.Alpha -> {
                val a = (color.a / 255f) * pixelMode.blendFactor
                val c = 1f - a
                val old = target[x, y]
                val r = a * color.r + c * old.r
                val g = a * color.g + c * old.g
                val b = a * color.b + c * old.b
                target.set(x, y, Pixel.rgba(r.toInt(), g.toInt(), b.toInt()))
            }
        }

    override fun drawSpan(
        startX: Int,
        endX: Int,
        y: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (pixelMode == Pixel.Mode.Normal && target is RGBABuffer) {
            val width = target.width
            val height = target.height
            if (startX in 0..<width && endX in 0..<width && y in 0..<height) {
                val buffer = target.resource
                val index = (y * width + startX) * INT
                buffer.fillInts(index, endX - startX + 1, color.nativeRGBA)
                return
            }
        }

        for (x in startX..endX) {
            draw(x, y, color, target, pixelMode)
        }
    }

    override fun copySpan(
        startX: Int,
        y: Int,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
        source: Sprite,
        sourceX: Int,
        sourceY: Int,
        length: Int,
    ) {
        if (pixelMode == Pixel.Mode.Normal && target is RGBABuffer && source.sampleMode == Sprite.SampleMode.NORMAL) {
            val targetWidth = target.width
            val targetHeight = target.height
            val sourceWidth = source.width
            val sourceHeight = source.height
            if (
                length > 0 &&
                startX in 0..<targetWidth && startX + length - 1 in 0..<targetWidth && y in 0..<targetHeight &&
                sourceX in 0..<sourceWidth && sourceX + length - 1 in 0..<sourceWidth && sourceY in 0..<sourceHeight
            ) {
                val targetBuffer = target.resource
                val sourceBuffer = source.resource
                val targetIndex = (y * targetWidth + startX) * INT
                val sourceIndex = (sourceY * sourceWidth + sourceX) * INT
                targetBuffer.copyInts(targetIndex, sourceBuffer, sourceIndex, length)
                return
            }
        }

        for (i in 0..<length) {
            draw(startX + i, y, source[sourceX + i, sourceY], target, pixelMode)
        }
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
