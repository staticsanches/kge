package dev.staticsanches.kge.image

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
interface Pixmap : Sequence<Pixel> {
    /**
     * The out-of-bounds policy of the surface: NORMAL reads transparent,
     * PERIODIC wraps by `abs(coord % dimension)`, CLAMP snaps to the nearest
     * edge.
     */
    enum class SampleMode { NORMAL, PERIODIC, CLAMP }

    val width: Int

    val height: Int

    val sampleMode: SampleMode

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
    abstract fun uncheckedGet(
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
}

/**
 * A [Pixmap] with writable pixels.
 *
 * The default bodies own [set] (bounds-checked), [clear] and [inv]; the
 * concrete surface supplies [uncheckedSet] and may override [clear] — Sprite
 * fills its native buffer directly.
 */
interface MutablePixmap : Pixmap {
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
    abstract fun uncheckedSet(
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
