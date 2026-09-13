package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer

/**
 * The draw seam: via the [Rasterizer] aggregate, the per-pixel write every
 * raster primitive resolves [Pixel.Mode] through. Out-of-bounds draws never
 * throw and never touch storage; the old pixel for Alpha/Custom is the stored
 * value, never a sample-mode wrap.
 */
interface DrawService : KGEOverridable {
    /**
     * Resolves [mode] against the stored pixel at ([x], [y]), writes the result
     * and returns whether a pixel was written — out of bounds, `false` for
     * every mode.
     */
    fun draw(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        color: Pixel,
        mode: Pixel.Mode,
    ): Boolean

    companion object :
        KGEOverridable.Proxy<DrawService>(DrawService::class, DrawServiceDefault),
        DrawService {
        override fun draw(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ): Boolean = delegate.draw(target, x, y, color, mode)
    }
}

/** The platform-independent default. */
private object DrawServiceDefault : DrawService {
    override fun draw(
        target: Pixmap.Mutable,
        x: Int,
        y: Int,
        color: Pixel,
        mode: Pixel.Mode,
    ): Boolean =
        when (mode) {
            Pixel.Mode.Normal -> target.set(x, y, color)
            Pixel.Mode.Mask -> color.a == 255 && target.set(x, y, color)
            is Pixel.Mode.Alpha -> blendAlpha(target, x, y, color, mode)
            is Pixel.Mode.Custom -> applyCustom(target, x, y, color, mode)
        }
}

private fun blendAlpha(
    target: Pixmap.Mutable,
    x: Int,
    y: Int,
    color: Pixel,
    mode: Pixel.Mode.Alpha,
): Boolean {
    if (x !in 0 until target.width || y !in 0 until target.height) return false
    val old = target.uncheckedGet(x, y)
    val a = (color.a / 255f) * mode.blendFactor
    val c = 1f - a
    val r = a * color.r + c * old.r
    val g = a * color.g + c * old.g
    val b = a * color.b + c * old.b
    target.uncheckedSet(x, y, Pixel.rgba(r.toInt(), g.toInt(), b.toInt()))
    return true
}

private fun applyCustom(
    target: Pixmap.Mutable,
    x: Int,
    y: Int,
    color: Pixel,
    mode: Pixel.Mode.Custom,
): Boolean {
    if (x !in 0 until target.width || y !in 0 until target.height) return false
    val old = target.uncheckedGet(x, y)
    target.uncheckedSet(x, y, mode.apply(x, y, color, old))
    return true
}
