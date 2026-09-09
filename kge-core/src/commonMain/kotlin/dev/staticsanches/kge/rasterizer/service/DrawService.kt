package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.MutablePixmap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer

/**
 * The draw seam: the per-pixel write every raster primitive resolves
 * [Pixel.Mode] through. [draw] bounds-checks before it reads, so an
 * out-of-bounds draw never throws and never touches the storage; the old
 * pixel for Alpha/Custom is always the stored value, never a sample-mode
 * wrap. The other sub-services ([OutlineService], [FillService],
 * [DrawSpriteService]) resolve their per-pixel work here via the
 * [Rasterizer] aggregate, so an override of this service is observed by
 * every primitive.
 */
interface DrawService : KGEOverridable {
    /**
     * Resolves [mode] against the stored pixel at ([x], [y]) and writes the
     * result, returning whether a pixel was written. Out of bounds no pixel
     * is written and `false` is returned, whatever the mode.
     */
    fun draw(
        target: MutablePixmap,
        x: Int,
        y: Int,
        color: Pixel,
        mode: Pixel.Mode,
    ): Boolean

    companion object :
        KGEOverridable.Proxy<DrawService>(DrawService::class, drawServiceDefault),
        DrawService {
        override fun draw(
            target: MutablePixmap,
            x: Int,
            y: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ): Boolean = delegate.draw(target, x, y, color, mode)
    }
}

/** The platform-independent default — the mode math is pure CPU over the surface accessors. */
private val drawServiceDefault: DrawService =
    object : DrawService {
        override fun draw(
            target: MutablePixmap,
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
    target: MutablePixmap,
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
    target: MutablePixmap,
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
