@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGEAllOpen
import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer.Type.RGBA
import dev.staticsanches.kge.image.pixelmap.buffer.RGBABuffer
import dev.staticsanches.kge.image.service.PixelBufferService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import java.io.InputStream
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Representation of an image in KGE.
 */
@KGEAllOpen
class Sprite(
    @property:KGESensitiveAPI val pixmap: RGBABuffer,
    var sampleMode: SampleMode,
) : PixelMap by pixmap, KGEResource by pixmap {
    override operator fun get(
        x: Int,
        y: Int,
    ): Pixel =
        when (sampleMode) {
            SampleMode.NORMAL -> if (x in 0..<width && y in 0..<height) uncheckedGet(x, y) else Colors.BLANK
            SampleMode.PERIODIC -> uncheckedGet(abs(x % width), abs(y % height))
            SampleMode.CLAMP -> uncheckedGet(max(0, min(x, width - 1)), max(0, min(y, height - 1)))
        }

    override fun duplicate(): Sprite = Sprite(pixmap.duplicate(), sampleMode)

    fun duplicate(
        offset: Int2D,
        size: Int2D,
    ): Sprite = create(size.x, size.y, sampleMode) { x, y -> this[x + offset.x, y + offset.y] }

    override fun toString(): String = "Sprite $pixmap"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Sprite) return false

        if (sampleMode != other.sampleMode) return false
        if (pixmap != other.pixmap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pixmap.hashCode()
        result = 31 * result + sampleMode.hashCode()
        return result
    }

    enum class SampleMode { NORMAL, PERIODIC, CLAMP }

    enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }

    companion object {
        fun create(
            width: Int,
            height: Int,
            sampleMode: SampleMode = SampleMode.NORMAL,
            defaultPixel: Pixel = Colors.BLACK,
        ): Sprite =
            Sprite(
                PixelBufferService.create(RGBA, width, height).applyAndCloseIfFailed { it.clear(defaultPixel) },
                sampleMode,
            )

        fun create(
            width: Int,
            height: Int,
            sampleMode: SampleMode = SampleMode.NORMAL,
            pixelByXY: (x: Int, y: Int) -> Pixel,
        ): Sprite =
            Sprite(
                PixelBufferService.create(RGBA, width, height).applyAndCloseIfFailed { it.clear(pixelByXY) },
                sampleMode,
            )

        fun load(
            fileName: String,
            sampleMode: SampleMode = SampleMode.NORMAL,
        ): Sprite = Sprite(PixelBufferService.load(fileName), sampleMode)

        fun load(
            url: URL,
            sampleMode: SampleMode = SampleMode.NORMAL,
        ): Sprite = Sprite(PixelBufferService.load(url), sampleMode)

        fun load(
            isProvider: () -> InputStream,
            sampleMode: SampleMode = SampleMode.NORMAL,
        ): Sprite = Sprite(PixelBufferService.load(isProvider), sampleMode)
    }
}
