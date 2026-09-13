package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Viewport

/**
 * The clip seam: the line clip every [OutlineService.drawLine] resolves before
 * it walks. The default is integer Cohen–Sutherland (olc v2.30
 * `ClipLineToDrawTarget`), inclusive at the viewport's upper bound, with
 * truncating division. [Viewport] carries the region data only.
 */
interface ClipService : KGEOverridable {
    /**
     * Clips the segment [start]–[end] to [viewport], returning the clipped
     * endpoints or `null` when the segment lies entirely outside. An
     * [Viewport.Unbounded] viewport never rejects.
     */
    fun clipLineTo(
        viewport: Viewport,
        start: Int2D,
        end: Int2D,
    ): Pair<Int2D, Int2D>?

    companion object :
        KGEOverridable.Proxy<ClipService>(ClipService::class, ClipServiceDefault),
        ClipService {
        override fun clipLineTo(
            viewport: Viewport,
            start: Int2D,
            end: Int2D,
        ): Pair<Int2D, Int2D>? = delegate.clipLineTo(viewport, start, end)
    }
}

/** The platform-independent default. */
private object ClipServiceDefault : ClipService {
    private const val SEG_L = 0b0001
    private const val SEG_R = 0b0010
    private const val SEG_B = 0b0100
    private const val SEG_T = 0b1000

    override fun clipLineTo(
        viewport: Viewport,
        start: Int2D,
        end: Int2D,
    ): Pair<Int2D, Int2D>? =
        when (viewport) {
            is Viewport.Unbounded -> start to end
            is Viewport.Bounded -> clip(start, end, viewport.lowerBoundInclusive, viewport.upperBoundExclusive)
            is Viewport.LowerBounded -> clip(start, end, viewport.lowerBoundInclusive, null)
            is Viewport.UpperBounded -> clip(start, end, null, viewport.upperBoundExclusive)
        }

    private fun clip(
        start: Int2D,
        end: Int2D,
        lower: Int2D?,
        upper: Int2D?,
    ): Pair<Int2D, Int2D>? {
        var p1 = start
        var p2 = end
        var s1 = outCode(p1, lower, upper)
        var s2 = outCode(p2, lower, upper)

        while (true) {
            if (s1 or s2 == 0) return p1 to p2
            if (s1 and s2 != 0) return null
            val s3 = maxOf(s1, s2)
            val n =
                when {
                    s3 and SEG_T != 0 -> {
                        val bound = upper!!
                        Int2D(p1.x + (p2.x - p1.x) * (bound.y - p1.y) / (p2.y - p1.y), bound.y)
                    }

                    s3 and SEG_B != 0 -> {
                        val bound = lower!!
                        Int2D(p1.x + (p2.x - p1.x) * (bound.y - p1.y) / (p2.y - p1.y), bound.y)
                    }

                    s3 and SEG_R != 0 -> {
                        val bound = upper!!
                        Int2D(bound.x, p1.y + (p2.y - p1.y) * (bound.x - p1.x) / (p2.x - p1.x))
                    }

                    else -> {
                        val bound = lower!!
                        Int2D(bound.x, p1.y + (p2.y - p1.y) * (bound.x - p1.x) / (p2.x - p1.x))
                    }
                }
            if (s3 == s1) {
                p1 = n
                s1 = outCode(p1, lower, upper)
            } else {
                p2 = n
                s2 = outCode(p2, lower, upper)
            }
        }
    }

    private fun outCode(
        p: Int2D,
        lower: Int2D?,
        upper: Int2D?,
    ): Int {
        var code = 0
        if (lower != null) {
            if (p.x < lower.x) code = code or SEG_L
            if (p.y < lower.y) code = code or SEG_B
        }
        if (upper != null) {
            if (p.x > upper.x) code = code or SEG_R
            if (p.y > upper.y) code = code or SEG_T
        }
        return code
    }
}
