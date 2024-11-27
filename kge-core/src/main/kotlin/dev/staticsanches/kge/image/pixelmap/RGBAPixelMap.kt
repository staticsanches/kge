package dev.staticsanches.kge.image.pixelmap

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixel
import java.nio.ByteBuffer

/**
 * [PixelMap] that may store [Pixel]s in [pixelsData] with RGBA values.
 */
interface OptionalRGBAPixelMap : PixelMap {
    @KGESensitiveAPI
    val pixelsData: ByteBuffer?
        get() = null
}

/**
 * [PixelMap] that stores [Pixel]s in [pixelsData] with RGBA values.
 */
interface RGBAPixelMap : OptionalRGBAPixelMap {
    @KGESensitiveAPI
    override val pixelsData: ByteBuffer
}

interface MutableRGBAPixelMap :
    RGBAPixelMap,
    MutablePixelMap
