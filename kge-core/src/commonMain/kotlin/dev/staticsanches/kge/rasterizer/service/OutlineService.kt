package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.MutablePixmap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer
import kotlin.math.abs

/**
 * The outline family: best-fit integer lines, inclusive rectangles and the
 * reference midpoint circles. Every painted cell resolves its [Pixel.Mode]
 * through [DrawService.draw] via the [Rasterizer] aggregate, and the
 * rectangle/triangle composites draw their edges through
 * [OutlineService.drawLine] — so an override of either sub-service is
 * observed by the composites of this family.
 */
interface OutlineService : KGEOverridable {
    /**
     * Draws the best-fit integer line from ([x0], [y0]) to ([x1], [y1]),
     * resolving [mode] per pixel through the draw seam. The segment is clipped
     * to [target] through [ClipService] first — a fully outside line is a
     * no-op — and the walk then runs over the clipped endpoints, inclusive,
     * while the error term keeps the original deltas (the reference's
     * clip-then-walk). When a pixel is equidistant from the ideal line, the
     * step toward the diagonal neighbor wins.
     */
    fun drawLine(
        target: MutablePixmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Pixel,
        mode: Pixel.Mode,
    )

    /**
     * Draws the perimeter of the inclusive rectangle whose opposite corners
     * are ([x0], [y0]) and ([x1], [y1]) — the ring of the same box
     * [FillService.fillRect] fills, drawn as four [drawLine] calls, so either
     * corner order paints the same ring and the corners are written twice.
     */
    fun drawRect(
        target: MutablePixmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Pixel,
        mode: Pixel.Mode,
    )

    /**
     * Draws the reference midpoint circle of [radius] around ([cx], [cy]).
     * The painted cells are the midpoint rasterization, which can reach
     * beyond the exact `radius` (integer-arc cells); a radius of zero paints
     * the center only and a negative radius paints nothing.
     */
    fun drawCircle(
        target: MutablePixmap,
        cx: Int,
        cy: Int,
        radius: Int,
        color: Pixel,
        mode: Pixel.Mode,
    )

    /**
     * Draws the outline of the triangle with the given vertices, vertex order
     * irrelevant, as three [drawLine] calls along the edges — so each vertex
     * is the shared endpoint of two edges and is written twice (idempotent
     * under Normal, double-blended under Alpha). Collinear vertices draw the
     * line between the farthest pair once, and a triangle sharing no pixel
     * with the target paints nothing.
     */
    fun drawTriangle(
        target: MutablePixmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        mode: Pixel.Mode,
    )

    /** The [Int2D] form of [drawLine] — unpacks the points to the raw method. */
    fun drawLine(
        target: MutablePixmap,
        start: Int2D,
        end: Int2D,
        color: Pixel,
        mode: Pixel.Mode,
    ): Unit = drawLine(target, start.x, start.y, end.x, end.y, color, mode)

    /** The [Int2D] form of [drawRect] — unpacks the diagonal corners to the raw method. */
    fun drawRect(
        target: MutablePixmap,
        diagonalStart: Int2D,
        diagonalEnd: Int2D,
        color: Pixel,
        mode: Pixel.Mode,
    ): Unit = drawRect(target, diagonalStart.x, diagonalStart.y, diagonalEnd.x, diagonalEnd.y, color, mode)

    /** The [Int2D] form of [drawCircle] — unpacks the center to the raw method. */
    fun drawCircle(
        target: MutablePixmap,
        center: Int2D,
        radius: Int,
        color: Pixel,
        mode: Pixel.Mode,
    ): Unit = drawCircle(target, center.x, center.y, radius, color, mode)

    /** The [Int2D] form of [drawTriangle] — unpacks the vertices to the raw method. */
    fun drawTriangle(
        target: MutablePixmap,
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        mode: Pixel.Mode,
    ): Unit = drawTriangle(target, p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, color, mode)

    companion object :
        KGEOverridable.Proxy<OutlineService>(OutlineService::class, outlineServiceDefault),
        OutlineService {
        override fun drawLine(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawLine(target, x0, y0, x1, y1, color, mode)

        override fun drawRect(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawRect(target, x0, y0, x1, y1, color, mode)

        override fun drawCircle(
            target: MutablePixmap,
            cx: Int,
            cy: Int,
            radius: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawCircle(target, cx, cy, radius, color, mode)

        override fun drawTriangle(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawTriangle(target, x0, y0, x1, y1, x2, y2, color, mode)

        override fun drawLine(
            target: MutablePixmap,
            start: Int2D,
            end: Int2D,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawLine(target, start, end, color, mode)

        override fun drawRect(
            target: MutablePixmap,
            diagonalStart: Int2D,
            diagonalEnd: Int2D,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawRect(target, diagonalStart, diagonalEnd, color, mode)

        override fun drawCircle(
            target: MutablePixmap,
            center: Int2D,
            radius: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawCircle(target, center, radius, color, mode)

        override fun drawTriangle(
            target: MutablePixmap,
            p0: Int2D,
            p1: Int2D,
            p2: Int2D,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.drawTriangle(target, p0, p1, p2, color, mode)
    }
}

/** The platform-independent default — the algorithms are pure CPU over the surface accessors. */
private val outlineServiceDefault: OutlineService =
    object : OutlineService {
        override fun drawLine(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) {
            val dx = x1 - x0
            val dy = y1 - y0

            val (clippedStart, clippedEnd) =
                ClipService.clipLineTo(target, Int2D(x0, y0), Int2D(x1, y1)) ?: return
            val cx0 = clippedStart.x
            val cy0 = clippedStart.y
            val cx1 = clippedEnd.x
            val cy1 = clippedEnd.y

            if (dx == 0) {
                for (y in minOf(cy0, cy1)..maxOf(cy0, cy1)) {
                    DrawService.draw(target, cx0, y, color, mode)
                }
                return
            }

            if (dy == 0) {
                for (x in minOf(cx0, cx1)..maxOf(cx0, cx1)) {
                    DrawService.draw(target, x, cy0, color, mode)
                }
                return
            }

            val dxAbs = abs(dx)
            val dyAbs = abs(dy)
            var px = 2 * dyAbs - dxAbs
            var py = 2 * dxAbs - dyAbs

            if (dyAbs <= dxAbs) {
                var x: Int
                var y: Int
                val xEnd: Int
                if (dx >= 0) {
                    x = cx0
                    y = cy0
                    xEnd = cx1
                } else {
                    x = cx1
                    y = cy1
                    xEnd = cx0
                }

                DrawService.draw(target, x, y, color, mode)
                while (x < xEnd) {
                    x++
                    if (px < 0) {
                        px += 2 * dyAbs
                    } else {
                        y = if (dx > 0 == dy > 0) y + 1 else y - 1
                        px += 2 * (dyAbs - dxAbs)
                    }
                    DrawService.draw(target, x, y, color, mode)
                }
            } else {
                var x: Int
                var y: Int
                val yEnd: Int
                if (dy >= 0) {
                    x = cx0
                    y = cy0
                    yEnd = cy1
                } else {
                    x = cx1
                    y = cy1
                    yEnd = cy0
                }

                DrawService.draw(target, x, y, color, mode)
                while (y < yEnd) {
                    y++
                    if (py <= 0) {
                        py += 2 * dxAbs
                    } else {
                        x = if (dx > 0 == dy > 0) x + 1 else x - 1
                        py += 2 * (dxAbs - dyAbs)
                    }
                    DrawService.draw(target, x, y, color, mode)
                }
            }
        }

        override fun drawRect(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) {
            val left = minOf(x0, x1)
            val top = minOf(y0, y1)
            val right = maxOf(x0, x1)
            val bottom = maxOf(y0, y1)
            OutlineService.drawLine(target, left, top, right, top, color, mode)
            OutlineService.drawLine(target, right, top, right, bottom, color, mode)
            OutlineService.drawLine(target, right, bottom, left, bottom, color, mode)
            OutlineService.drawLine(target, left, bottom, left, top, color, mode)
        }

        override fun drawCircle(
            target: MutablePixmap,
            cx: Int,
            cy: Int,
            radius: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) {
            if (!circleTouchesTarget(target, cx, cy, radius)) return

            if (radius == 0) {
                DrawService.draw(target, cx, cy, color, mode)
                return
            }

            var x = 0
            var y = radius
            var d = 3 - 2 * radius
            while (y >= x) {
                DrawService.draw(target, cx + x, cy - y, color, mode)
                DrawService.draw(target, cx + y, cy + x, color, mode)
                DrawService.draw(target, cx - x, cy + y, color, mode)
                DrawService.draw(target, cx - y, cy - x, color, mode)
                if (x != 0 && x != y) {
                    DrawService.draw(target, cx + y, cy - x, color, mode)
                    DrawService.draw(target, cx + x, cy + y, color, mode)
                    DrawService.draw(target, cx - y, cy + x, color, mode)
                    DrawService.draw(target, cx - x, cy - y, color, mode)
                }
                if (d < 0) {
                    d += 4 * x + 6
                } else {
                    d += 4 * (x - y) + 10
                    y--
                }
                x++
            }
        }

        override fun drawTriangle(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) {
            val area =
                (x1 - x0).toLong() * (y2 - y0) -
                    (y1 - y0).toLong() * (x2 - x0)
            if (area == 0L) {
                drawFarthestPairLine(target, x0, y0, x1, y1, x2, y2, color, mode)
                return
            }

            OutlineService.drawLine(target, x0, y0, x1, y1, color, mode)
            OutlineService.drawLine(target, x1, y1, x2, y2, color, mode)
            OutlineService.drawLine(target, x2, y2, x0, y0, color, mode)
        }
    }

/** Whether the circle around ([cx], [cy]) of [radius] can reach any cell of [target]. */
internal fun circleTouchesTarget(
    target: MutablePixmap,
    cx: Int,
    cy: Int,
    radius: Int,
): Boolean =
    radius >= 0 &&
        cx >= -radius &&
        cy >= -radius &&
        cx - target.width <= radius &&
        cy - target.height <= radius

/** Collinear vertices: a single [OutlineService.drawLine] between the farthest pair. */
internal fun drawFarthestPairLine(
    target: MutablePixmap,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
    color: Pixel,
    mode: Pixel.Mode,
) {
    val pairs =
        arrayOf(
            x0 to y0 to (x1 to y1),
            x1 to y1 to (x2 to y2),
            x0 to y0 to (x2 to y2),
        )
    val (start, end) =
        pairs.maxBy { (a, b) -> maxOf(abs(b.first - a.first), abs(b.second - a.second)) }
    OutlineService.drawLine(target, start.first, start.second, end.first, end.second, color, mode)
}
