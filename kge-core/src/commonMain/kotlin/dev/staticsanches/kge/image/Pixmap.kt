package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Viewport
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * A 2D pixel surface, row-major over conventional x/y coordinates.
 *
 * The interface owns the algorithms; a concrete surface ([Sprite]) supplies
 * only the raw accessors and the storage. Coordinates outside the surface do
 * not throw — [get] follows the [sampleMode] policy and [sample]/[sampleBL]
 * dispatch through it, so sampling is mode-aware everywhere.
 */
interface Pixmap :
    Sequence<Pixel>,
    Viewport.Bounded {
    /**
     * The out-of-bounds policy of the surface: NORMAL reads transparent,
     * PERIODIC wraps by `abs(coord % dimension)`, CLAMP snaps to the nearest
     * edge.
     */
    enum class SampleMode { NORMAL, PERIODIC, CLAMP }

    /**
     * The source-read policy for a blit: which axes of the source are read in
     * reverse. [NONE] blits top-left to top-left; each flip mirrors the source
     * inside the same destination footprint.
     */
    enum class Flip { NONE, HORIZONTAL, VERTICAL, BOTH }

    val width: Int

    val height: Int

    val sampleMode: SampleMode

    override val lowerBoundInclusive: Int2D
        get() = Int2D.ZERO

    override val upperBoundExclusive: Int2D
        get() = Int2D(width, height)

    /**
     * A view over the sub-rectangle at [origin] of [size] pixels, in its own
     * local `0..size` space. The source must contain the region, else
     * [IllegalArgumentException]. The view reads through; the [Pixmap.Mutable]
     * override also writes through. A [Pixmap.RawBacked] source yields a
     * [Pixmap.RawBacked] view whose backing composes the origin, so a nested
     * window still reaches the root storage.
     */
    @OptIn(KGESensitiveAPI::class)
    fun window(
        origin: Int2D,
        size: Int2D,
    ): Pixmap {
        requireValidWindow(this, origin, size)
        val source = this
        return if (source is RawBacked) {
            object : Pixmap, RawBacked {
                override val width: Int = size.x
                override val height: Int = size.y
                override var sampleMode: SampleMode = source.sampleMode
                override val buffer: ByteBuffer get() = source.buffer
                override val stride: Int get() = source.stride
                override val baseIndex: Int = source.baseIndex + origin.y * source.stride + origin.x

                override fun uncheckedGet(
                    x: Int,
                    y: Int,
                ): Pixel = source.uncheckedGet(x + origin.x, y + origin.y)
            }
        } else {
            object : Pixmap {
                override val width: Int = size.x
                override val height: Int = size.y
                override var sampleMode: SampleMode = source.sampleMode

                override fun uncheckedGet(
                    x: Int,
                    y: Int,
                ): Pixel = source.uncheckedGet(x + origin.x, y + origin.y)
            }
        }
    }

    /**
     * Reads the pixel at ([x], [y]), never throwing — the out-of-bounds value
     * follows the [sampleMode] policy.
     */
    fun get(
        x: Int,
        y: Int,
    ): Pixel =
        when (sampleMode) {
            SampleMode.NORMAL ->
                if (x in 0 until width && y in 0 until height) {
                    uncheckedGet(x, y)
                } else {
                    Colors.TRANSPARENT
                }

            SampleMode.PERIODIC -> uncheckedGet(abs(x % width), abs(y % height))
            SampleMode.CLAMP -> uncheckedGet(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
        }

    /** Reads `(x, y)` in-bounds only — the hot path. */
    fun uncheckedGet(
        x: Int,
        y: Int,
    ): Pixel

    /**
     * The nearest-neighbor pixel at unit coordinates `(u, v)`, `u`/`v`
     * clamped high (beyond 1 the last line is kept) and any remaining
     * out-of-bounds case dispatched through [get].
     */
    fun sample(
        u: Float,
        v: Float,
    ): Pixel =
        get(
            min((u * width).toInt(), width - 1),
            min((v * height).toInt(), height - 1),
        )

    /**
     * Bilinear blend: the four [get] neighbors of the fractional texel at
     * `(u * width - 0.5, v * height - 0.5)`, the RGB channels weighted and
     * truncated to bytes, the alpha channel forced to 255.
     */
    fun sampleBL(
        u: Float,
        v: Float,
    ): Pixel {
        val sx = u * width - 0.5f
        val sy = v * height - 0.5f
        val fx = floor(sx).toInt()
        val fy = floor(sy).toInt()
        val wx = sx - fx
        val wy = sy - fy
        val c00 = get(fx, fy)
        val c10 = get(fx + 1, fy)
        val c01 = get(fx, fy + 1)
        val c11 = get(fx + 1, fy + 1)
        val r = c00.r * (1f - wx) * (1f - wy) + c10.r * wx * (1f - wy) + c01.r * (1f - wx) * wy + c11.r * wx * wy
        val g = c00.g * (1f - wx) * (1f - wy) + c10.g * wx * (1f - wy) + c01.g * (1f - wx) * wy + c11.g * wx * wy
        val b = c00.b * (1f - wx) * (1f - wy) + c10.b * wx * (1f - wy) + c01.b * (1f - wx) * wy + c11.b * wx * wy
        return Pixel.rgba(r.toInt(), g.toInt(), b.toInt(), 0xFF)
    }

    /** Row-major: `y * width + x`, pixels enumerated in storage order. */
    override fun iterator(): Iterator<Pixel> = PixmapRowMajorIterator(this)

    /**
     * A [Pixmap] with writable pixels.
     *
     * The default bodies own [set] (bounds-checked), [clear] and [inv]; the
     * concrete surface supplies [uncheckedSet] and may override [clear] —
     * Sprite fills its native buffer directly.
     */
    interface Mutable : Pixmap {
        /** The writable read policy: a [Pixmap.Mutable] view owns its [sampleMode]. */
        override var sampleMode: SampleMode

        /**
         * The writable [Pixmap.window]: reads and writes reach the source. A
         * [Pixmap.RawBacked] source yields a [Pixmap.RawBacked] view whose
         * backing composes the origin.
         */
        @OptIn(KGESensitiveAPI::class)
        override fun window(
            origin: Int2D,
            size: Int2D,
        ): Mutable {
            requireValidWindow(this, origin, size)
            val source = this
            return if (source is RawBacked) {
                object : Mutable, RawBacked {
                    override val width: Int = size.x
                    override val height: Int = size.y
                    override var sampleMode: SampleMode = source.sampleMode
                    override val buffer: ByteBuffer get() = source.buffer
                    override val stride: Int get() = source.stride
                    override val baseIndex: Int = source.baseIndex + origin.y * source.stride + origin.x

                    override fun uncheckedGet(
                        x: Int,
                        y: Int,
                    ): Pixel = source.uncheckedGet(x + origin.x, y + origin.y)

                    override fun uncheckedSet(
                        x: Int,
                        y: Int,
                        pixel: Pixel,
                    ) = source.uncheckedSet(x + origin.x, y + origin.y, pixel)
                }
            } else {
                object : Mutable {
                    override val width: Int = size.x
                    override val height: Int = size.y
                    override var sampleMode: SampleMode = source.sampleMode

                    override fun uncheckedGet(
                        x: Int,
                        y: Int,
                    ): Pixel = source.uncheckedGet(x + origin.x, y + origin.y)

                    override fun uncheckedSet(
                        x: Int,
                        y: Int,
                        pixel: Pixel,
                    ) = source.uncheckedSet(x + origin.x, y + origin.y, pixel)
                }
            }
        }

        /**
         * Writes [pixel] at ([x], [y]), returning false without touching the
         * surface when the coordinates are outside — never throws.
         */
        fun set(
            x: Int,
            y: Int,
            pixel: Pixel,
        ): Boolean {
            if (x in 0 until width && y in 0 until height) {
                uncheckedSet(x, y, pixel)
                return true
            }
            return false
        }

        /** Writes `(x, y)` in-bounds only — the hot path. */
        fun uncheckedSet(
            x: Int,
            y: Int,
            pixel: Pixel,
        )

        /** Fills every pixel with [pixel]. */
        fun clear(pixel: Pixel) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    uncheckedSet(x, y, pixel)
                }
            }
        }

        /** Replaces each pixel with its [Pixel.inv], keeping the alpha channel. */
        fun inv() {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    uncheckedSet(x, y, uncheckedGet(x, y).inv())
                }
            }
        }
    }

    /**
     * A [Pixmap] whose pixels sit in the raw [buffer]: a row [stride] in int
     * elements and the [baseIndex] of the view's local `(0, 0)`. A surface
     * that implements it always has contiguous storage; a surface without one
     * (an algorithmic surface, a view over a non-contiguous source) simply
     * does not implement this interface. [buffer] resolves the owning resource
     * on demand, so the release fail-fast after close is preserved, and
     * [index] maps a local pixel to its int-element offset — multiply by
     * [Int.SIZE_BYTES] for the byte offset the buffer API expects.
     */
    @KGESensitiveAPI
    interface RawBacked : Pixmap {
        /** The raw int-element buffer; fails fast once the owning resource is closed. */
        val buffer: ByteBuffer

        /** The number of int elements per row. */
        val stride: Int

        /** The int-element index of the view's local `(0, 0)`. */
        val baseIndex: Int

        /** The int-element offset of ([x], [y]): `baseIndex + y * stride + x`. */
        fun index(
            x: Int,
            y: Int,
        ): Int = baseIndex + y * stride + x
    }
}

private fun requireValidWindow(
    source: Pixmap,
    origin: Int2D,
    size: Int2D,
) {
    require(size.x > 0 && size.y > 0) { "window size must be positive: $size" }
    require(origin.x >= 0 && origin.y >= 0) { "window origin must be non-negative: $origin" }
    require(origin.x + size.x <= source.width && origin.y + size.y <= source.height) {
        "window origin $origin size $size is outside ${source.width}x${source.height}"
    }
}

private class PixmapRowMajorIterator(
    private val pixmap: Pixmap,
) : Iterator<Pixel> {
    private var index = 0

    override fun hasNext(): Boolean = index < pixmap.width * pixmap.height

    override fun next(): Pixel =
        pixmap
            .uncheckedGet(index % pixmap.width, index / pixmap.width)
            .also { index++ }
}
