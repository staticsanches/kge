@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.wrapper.ByteBufferWrapper
import dev.staticsanches.kge.image.service.PixelService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.utils.BytesSize.INT
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * It is logically a 2D matrix of [Pixel]s with a "row major" configuration and "direct access"
 * by coordinates (x, y).
 *
 * (0, 0) indicates the top-left corner and ([width] - 1, [height] - 1) indicates the bottom-right corner.
 */
interface PixelMap :
    Sequence<Pixel>,
    Viewport.Bounded {
    /**
     * The number of columns.
     */
    val width: Int

    /**
     * The number of rows.
     */
    val height: Int

    /**
     * [width] x [height].
     */
    val size: Int2D

    override val lowerBoundInclusive: Int2D
        get() = Int2D.zeroByZero

    override val upperBoundExclusive: Int2D
        get() = size

    operator fun get(
        x: Int,
        y: Int,
    ): Pixel {
        if (x !in 0..<width || y !in 0..<height) {
            throw IndexOutOfBoundsException(
                "Coordinates ($x, $y) does not comply to 0 <= x < $width and 0 <= y < $height",
            )
        }
        return uncheckedGet(x, y)
    }

    fun uncheckedGet(
        x: Int,
        y: Int,
    ): Pixel

    fun getPixel(
        x: Int,
        y: Int,
    ): Pixel = this[x, y]

    fun getPixel(position: Int2D): Pixel = this[position]

    operator fun get(position: Int2D): Pixel = this[position.x, position.y]

    fun sample(uv: Float2D): Pixel = sample(uv.x, uv.y)

    fun sample(
        u: Float,
        v: Float,
    ): Pixel = this[min((u * width).toInt(), width - 1), min((v * height).toInt(), height - 1)]

    fun sampleBL(uv: Float2D): Pixel = sampleBL(uv.x, uv.y)

    fun sampleBL(
        u: Float,
        v: Float,
    ): Pixel {
        val computedU = u * width - 0.5f
        val computedV = v * height - 0.5f

        val x = floor(computedU).toInt()
        val y = floor(computedV).toInt()
        val uRatio = computedU - x
        val vRatio = computedV - y
        val uOpposite = 1 - uRatio
        val vOpposite = 1 - vRatio

        val x0 = max(x, 0)
        val y0 = max(y, 0)
        val x1 = min(x + 1, width - 1)
        val y1 = min(y + 1, height - 1)

        val p1 = this[x0, y0]
        val p2 = this[x1, y0]
        val p3 = this[x0, y1]
        val p4 = this[x1, y1]

        fun calculateComponent(componentGetter: (Pixel) -> IntColorComponent): Float {
            val v1 = componentGetter(p1)
            val v2 = componentGetter(p2)
            val v3 = componentGetter(p3)
            val v4 = componentGetter(p4)

            return ((v1 * uOpposite + v2 * uRatio) * vOpposite + (v3 * uOpposite + v4 * uRatio) * vRatio)
        }

        return Pixel.rgba(calculateComponent(Pixel::r), calculateComponent(Pixel::g), calculateComponent(Pixel::b))
    }

    override fun iterator(): Iterator<Pixel> =
        iterator {
            for (y in 0..<height) {
                for (x in 0..<width) {
                    yield(uncheckedGet(x, y))
                }
            }
        }
}

/**
 * [PixelMap] that allows modifications of its [Pixel]s.
 */
interface MutablePixelMap : PixelMap {
    operator fun set(
        x: Int,
        y: Int,
        pixel: Pixel,
    ): Boolean = x in 0..<width && y in 0..<height && uncheckedSet(x, y, pixel)

    fun uncheckedSet(
        x: Int,
        y: Int,
        pixel: Pixel,
    ): Boolean

    fun clear(pixel: Pixel)

    fun clear(pixelByXY: (x: Int, y: Int) -> Pixel)

    fun clear(pixels: Iterable<Pixel>)

    fun inv()

    fun setPixel(
        x: Int,
        y: Int,
        pixel: Pixel,
    ): Boolean = set(x, y, pixel)

    fun setPixel(
        position: Int2D,
        pixel: Pixel,
    ): Boolean = set(position, pixel)

    operator fun set(
        position: Int2D,
        pixel: Pixel,
    ): Boolean = set(position.x, position.y, pixel)
}

/**
 * [MutablePixelMap] that store [Pixel]s in a [ByteBuffer], using RGBA values.
 */
interface RGBABuffer :
    MutablePixelMap,
    ByteBufferWrapper {
    override fun uncheckedGet(
        x: Int,
        y: Int,
    ): Pixel = Pixel(resource.getInt((y * width + x) * INT))

    override fun uncheckedSet(
        x: Int,
        y: Int,
        pixel: Pixel,
    ): Boolean {
        resource.putInt((y * width + x) * INT, pixel.nativeRGBA)
        return true
    }

    override fun clear(pixel: Pixel) =
        with(resource) {
            clear()
            while (hasRemaining()) {
                putInt(pixel.nativeRGBA)
            }
        }

    override fun clear(pixelByXY: (Int, Int) -> Pixel) =
        with(resource) {
            clear()
            for (y in 0..<height) {
                for (x in 0..<width) {
                    putInt(pixelByXY(x, y).nativeRGBA)
                }
            }
        }

    override fun clear(pixels: Iterable<Pixel>) =
        with(resource) {
            clear()
            val iterator = pixels.iterator()
            while (hasRemaining() && iterator.hasNext()) {
                putInt(iterator.next().nativeRGBA)
            }
        }

    override fun inv() = PixelService.invRGBABuffer(resource)
}
