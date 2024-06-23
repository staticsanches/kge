package dev.staticsanches.kge.image.pixelmap.buffer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer.Type
import dev.staticsanches.kge.image.service.PixelBufferService
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.by
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.utils.humanReadableByteCountBin
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.inv

/**
 * [ByteBuffer] wrapper to allow it to function as a [PixelMap].
 */
sealed class PixelBuffer<PB : PixelBuffer<PB, T>, T : Type<PB, T>>(
    final override val width: Int,
    final override val height: Int,
    val type: T,
    @property:KGESensitiveAPI val internalBuffer: ByteBuffer,
) : PixelMap,
    KGEResource {
    init {
        check(width > 0 && height > 0) { "Invalid buffer dimension ${width}x$height" }

        val expectedBufferCapacity = type.expectedBufferCapacity(width, height)
        check(expectedBufferCapacity == internalBuffer.capacity()) {
            "Invalid buffer capacity. Expected: $expectedBufferCapacity. Actual: ${internalBuffer.capacity()}"
        }

        check(internalBuffer.order() == ByteOrder.nativeOrder()) {
            "Invalid order for the buffer. It must use the native order: ${ByteOrder.nativeOrder()}"
        }
    }

    final override val size: Int2D = width by height

    override fun inv() {
        for (i in 0..<internalBuffer.capacity()) {
            internalBuffer.put(i, internalBuffer[i].inv())
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun duplicate(): PB = PixelBufferService.duplicate(this as PB)

    protected val representation =
        "${type::class.java.simpleName} ${width}x$height (${
            humanReadableByteCountBin(internalBuffer.capacity().toLong())
        })"

    override fun toString(): String = representation

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PixelBuffer<*, *>

        if (width != other.width) return false
        if (height != other.height) return false
        if (type != other.type) return false
        if (internalBuffer.clear() != other.internalBuffer.clear()) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + type.hashCode()
        result = 31 * result + internalBuffer.clear().hashCode()
        return result
    }

    /**
     * Available [PixelBuffer] types.
     */
    sealed class Type<PB : PixelBuffer<PB, T>, T : Type<PB, T>> {
        /**
         * Represents images with 4-channels.
         *
         * @see RGBABuffer
         */
        data object RGBA : Type<RGBABuffer, RGBA>() {
            override fun expectedBufferCapacity(
                width: Int,
                height: Int,
            ): Int = width * height * 4
        }

        /**
         * Represents images with 3-channels.
         *
         * [defaultAlpha] is used when converting [RGB] -> [RGBA].
         *
         * [matteBackground] must be a matte color since its [Pixel.a] is
         * discarded by [PixelService.toRGB] when converting [RGBA] -> [RGB].
         *
         * @see RGBBuffer
         */
        data class RGB(
            val defaultAlpha: IntColorComponent = 0xFF,
            val matteBackground: Pixel = Colors.WHITE,
        ) : Type<RGBBuffer, RGB>() {
            override fun expectedBufferCapacity(
                width: Int,
                height: Int,
            ): Int = width * height * 3
        }

        /**
         * Represents images with 1-channel.
         *
         * [PixelService.toGrayscale] is used when converting [RGBA] -> [Grayscale].
         *
         * [PixelService.fromGrayscale] and [defaultAlpha] are used when converting [Grayscale] -> [RGBA].
         *
         * @see GrayscaleBuffer
         */
        data class Grayscale(
            val defaultAlpha: IntColorComponent = 0xFF,
        ) : Type<GrayscaleBuffer, Grayscale>() {
            override fun expectedBufferCapacity(
                width: Int,
                height: Int,
            ): Int = width * height
        }

        /**
         * Represents images with 1 bit per pixel (aka Black and White).
         *
         * [foreground] and [background] are used when converting [Bitmap] -> [RGBA].
         *
         * [PixelService.toRGB] and [PixelService.distance2] and [matteBackground] are used
         * when converting [RGBA] -> [Bitmap].
         *
         * @see BitmapBuffer
         */
        data class Bitmap(
            val foreground: Pixel = Colors.BLACK,
            val background: Pixel = Colors.WHITE,
            val matteBackground: Pixel = Colors.WHITE,
            val negativePitch: Boolean = false,
            val disableEvenPitch: Boolean = false,
        ) : Type<BitmapBuffer, Bitmap>() {
            /**
             * The expected number of bytes in a row.
             */
            fun expectedAbsolutePitch(width: Int): Int {
                var pitch = width / 8
                if (width % 8 == 0) {
                    pitch++
                }
                if (!disableEvenPitch && pitch % 2 == 1) {
                    pitch++
                }
                return pitch
            }

            override fun expectedBufferCapacity(
                width: Int,
                height: Int,
            ): Int = expectedAbsolutePitch(width) * height
        }

        /**
         * The expected [ByteBuffer.capacity] for this [Type].
         */
        abstract fun expectedBufferCapacity(
            width: Int,
            height: Int,
        ): Int
    }
}
