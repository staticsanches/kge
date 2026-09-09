package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.buffer.fillInts
import dev.staticsanches.kge.image.MutablePixmap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.rasterizer.Rasterizer

/**
 * The fill family: solid rectangles, circles and triangles over a
 * [MutablePixmap]. Every painted cell resolves its [Pixel.Mode] through
 * [DrawService.draw] via the [Rasterizer] aggregate; a collinear
 * triangle fills the line through [OutlineService.drawLine]. A [Sprite]
 * target additionally enables the private raw-row fast paths.
 */
interface FillService : KGEOverridable {
    /**
     * Fills the inclusive rectangle whose opposite corners are ([x0], [y0])
     * and ([x1], [y1]) — `(x1 - x0 + 1) x (y1 - y0 + 1)` pixels, so both
     * endpoint orders paint the same box. The part outside the target is
     * clipped away; a rectangle with no pixel in common paints nothing.
     */
    fun fillRect(
        target: MutablePixmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Pixel,
        mode: Pixel.Mode,
    )

    /**
     * Fills the reference midpoint circle of [radius] around ([cx], [cy])
     * — whole rows per arc step, no row painted twice. A radius of zero
     * paints the center only and a negative radius paints nothing.
     */
    fun fillCircle(
        target: MutablePixmap,
        cx: Int,
        cy: Int,
        radius: Int,
        color: Pixel,
        mode: Pixel.Mode,
    )

    /**
     * Fills the triangle with the given vertices, vertex order irrelevant.
     * A pixel is painted when its center is inside the triangle; a center
     * lying exactly on an edge is owned by the bottom/right edges, never by
     * the top/left ones — so two triangles sharing an edge paint the shared
     * boundary pixels exactly once. The row of a bottom vertex is not
     * painted (its pixel centers lie beyond the edge). Collinear vertices
     * draw the line between the farthest pair.
     */
    fun fillTriangle(
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

    companion object :
        KGEOverridable.Proxy<FillService>(FillService::class, fillServiceDefault),
        FillService {
        override fun fillRect(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.fillRect(target, x0, y0, x1, y1, color, mode)

        override fun fillCircle(
            target: MutablePixmap,
            cx: Int,
            cy: Int,
            radius: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.fillCircle(target, cx, cy, radius, color, mode)

        override fun fillTriangle(
            target: MutablePixmap,
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            color: Pixel,
            mode: Pixel.Mode,
        ) = delegate.fillTriangle(target, x0, y0, x1, y1, x2, y2, color, mode)
    }
}

/** The platform-independent default — the algorithms are pure CPU over the surface accessors. */
private val fillServiceDefault: FillService =
    object : FillService {
        override fun fillRect(
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
            if (right < 0 || bottom < 0 || left >= target.width || top >= target.height) return

            val clipLeft = maxOf(left, 0)
            val clipTop = maxOf(top, 0)
            val clipRight = minOf(right, target.width - 1)
            val clipBottom = minOf(bottom, target.height - 1)

            if (target is Sprite && mode == Pixel.Mode.Normal) {
                // the raw row fast path: NORMAL ignores alpha, so the int
                // pattern fill matches the draw seam verbatim write
                fillRectRawRows(target, clipLeft, clipTop, clipRight, clipBottom, color)
                return
            }

            for (y in clipTop..clipBottom) {
                for (x in clipLeft..clipRight) {
                    DrawService.draw(target, x, y, color, mode)
                }
            }
        }

        override fun fillCircle(
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
                fillRow(target, cx - y, cx + y, cy - x, color, mode)
                if (x > 0) {
                    fillRow(target, cx - y, cx + y, cy + x, color, mode)
                }
                if (d < 0) {
                    d += 4 * x + 6
                } else {
                    if (x != y) {
                        fillRow(target, cx - x, cx + x, cy - y, color, mode)
                        fillRow(target, cx - x, cx + x, cy + y, color, mode)
                    }
                    d += 4 * (x - y) + 10
                    y--
                }
                x++
            }
        }

        override fun fillTriangle(
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

            var ax = x0
            var ay = y0
            var bx = x1
            var by = y1
            var cx = x2
            var cy = y2
            if (area < 0) {
                // normalize the winding so the open/closed edge rule is geometric
                val t = bx
                bx = cx
                cx = t
                val u = by
                by = cy
                cy = u
            }

            val minX = minOf(ax, bx, cx)
            val maxX = maxOf(ax, bx, cx)
            val minY = minOf(ay, by, cy)
            val maxY = maxOf(ay, by, cy)
            if (maxX < 0 || minX >= target.width || maxY < 0 || minY >= target.height) return

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (insideTriangleHalfOpen(x, y, ax, ay, bx, by, cx, cy)) {
                        DrawService.draw(target, x, y, color, mode)
                    }
                }
            }
        }
    }

private fun insideTriangleHalfOpen(
    px: Int,
    py: Int,
    ax: Int,
    ay: Int,
    bx: Int,
    by: Int,
    cx: Int,
    cy: Int,
): Boolean {
    // the pixel center doubled, so the test stays in integers
    val sx = 2 * px + 1
    val sy = 2 * py + 1
    return edgeContains(ax, ay, bx, by, sx, sy) &&
        edgeContains(bx, by, cx, cy, sx, sy) &&
        edgeContains(cx, cy, ax, ay, sx, sy)
}

private fun edgeContains(
    ax: Int,
    ay: Int,
    bx: Int,
    by: Int,
    sx: Int,
    sy: Int,
): Boolean {
    val dx = bx - ax
    val dy = by - ay
    // for a CCW triangle: top/left edges are open, bottom/right edges closed
    val open = dy < 0 || (dy == 0 && dx > 0)
    val cross = dx * (sy - 2 * ay) - dy * (sx - 2 * ax)
    return if (open) cross > 0 else cross >= 0
}

private fun fillRow(
    target: MutablePixmap,
    fromX: Int,
    toX: Int,
    y: Int,
    color: Pixel,
    mode: Pixel.Mode,
) {
    for (x in fromX..toX) {
        DrawService.draw(target, x, y, color, mode)
    }
}

private fun fillRectRawRows(
    target: Sprite,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    color: Pixel,
) {
    val buffer = target.byteBuffer
    val rowLength = right - left + 1
    for (y in top..bottom) {
        buffer.fillInts((y * target.width + left) * Int.SIZE_BYTES, rowLength, color.nativeRGBA)
    }
}
