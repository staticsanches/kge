package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.CohenSutherlandOutCode
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.rasterizer.contains
import dev.staticsanches.kge.rasterizer.fittestX
import dev.staticsanches.kge.rasterizer.fittestY
import dev.staticsanches.kge.rasterizer.outCode
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

typealias BresenhamLine = Iterator<Int2D>

/**
 * Uses the [Bresenham's line algorithm](https://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm) to generate a line
 * between [start] point (inclusive) and [end] point (inclusive). The [viewport] is used to clip the result.
 */
fun BresenhamLine(
    start: Int2D,
    end: Int2D,
    viewport: Viewport,
): BresenhamLine {
    if (viewport.outCode(start) and viewport.outCode(end) != CohenSutherlandOutCode.INSIDE) {
        return Collections.emptyIterator() // outside viewport (trivial rejection)
    }

    val d = end - start
    return when {
        d == Int2D.zeroByZero -> SinglePoint(start)
        d.x == 0 -> Vertical.line(start, end, viewport)
        d.y == 0 -> Horizontal.line(start, end, viewport)
        d.x == d.y || d.x == -d.y -> Diagonal.line(start, end, viewport)
        else -> funkyLine(start, end, d, viewport)
    }
}

private class SinglePoint(
    private val next: Int2D,
) : BresenhamLine {
    private var hasNext = true

    override fun hasNext(): Boolean = hasNext

    override fun next(): Int2D {
        if (!hasNext) throw NoSuchElementException()
        hasNext = false
        return next
    }
}

private class Vertical private constructor(
    private val x: Int,
    private var y: Int,
    private val yInc: Int,
    private var dy: Int,
) : BresenhamLine {
    override fun hasNext(): Boolean = dy >= 0

    override fun next(): Int2D {
        if (!hasNext()) throw NoSuchElementException()

        val nextY = y
        y += yInc
        dy--
        return x by nextY
    }

    companion object {
        fun line(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): BresenhamLine {
            val x = start.x
            val y = viewport.fittestY(start)
            val dy = abs(viewport.fittestY(end) - y)
            val yInc = if (start.y < end.y) 1 else -1
            return Vertical(x, y, yInc, dy)
        }
    }
}

private class Horizontal private constructor(
    private var x: Int,
    private val y: Int,
    private val xInc: Int,
    private var dx: Int,
) : BresenhamLine {
    override fun hasNext(): Boolean = dx >= 0

    override fun next(): Int2D {
        if (!hasNext()) throw NoSuchElementException()

        val nextX = x
        x += xInc
        dx--
        return nextX by y
    }

    companion object {
        fun line(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): BresenhamLine {
            val x = viewport.fittestX(start)
            val y = start.y
            val dx = abs(viewport.fittestX(end) - x)
            val xInc = if (start.x < end.x) 1 else -1
            return Horizontal(x, y, xInc, dx)
        }
    }
}

private class Diagonal private constructor(
    private val xInc: Int,
    private val yInc: Int,
    private var x: Int,
    private var y: Int,
    private var d: Int,
) : BresenhamLine {
    override fun hasNext(): Boolean = d >= 0

    override fun next(): Int2D {
        if (!hasNext()) throw NoSuchElementException()

        val nextX = x
        val nextY = y
        x += xInc
        y += yInc
        d--
        return nextX by nextY
    }

    companion object {
        fun line(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): BresenhamLine {
            val xInc = if (start.x < end.x) 1 else -1
            val yInc = if (start.y < end.y) 1 else -1
            var (x, y) = start
            val delta = max(abs(viewport.fittestX(start) - x), abs(viewport.fittestY(start) - y))
            x += (xInc * delta)
            y += (yInc * delta)
            val d = min(abs(viewport.fittestX(end) - x), abs(viewport.fittestY(end) - y))
            return Diagonal(xInc, yInc, x, y, d)
        }
    }
}

private fun funkyLine(
    start: Int2D,
    end: Int2D,
    d: Int2D,
    viewport: Viewport,
): BresenhamLine = funkyLine(start.x, start.y, end.x, end.y, d.x, d.y, viewport, ::Int2D)

private fun funkyLine(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    dx: Int,
    dy: Int,
    viewport: Viewport,
    converter: (x: Int, y: Int) -> Int2D,
): BresenhamLine {
    if (dx > 0 && dy < 0 || dx < 0 && dy > 0) { // slope < 0: reflect on X-axis
        return funkyLine(startX, -startY, endX, -endY, dx, -dy, viewport) { x, y -> converter(x, -y) }
    }

    if (abs(dy) > abs(dx)) { // slope > 1: swap x and y
        return funkyLine(startY, startX, endY, endX, dy, dx, viewport) { x, y -> converter(y, x) }
    }

    return if (dx > 0) {
        AscendingFunky(startX, startY, endX, dx, dy, viewport, converter)
    } else {
        DescendingFunky(startX, startY, endX, dx, dy, viewport, converter)
    }
}

private class AscendingFunky(
    private var x: Int,
    private var y: Int,
    private val endX: Int,
    dx: Int,
    dy: Int,
    private val viewport: Viewport,
    private val converter: (x: Int, y: Int) -> Int2D,
) : BresenhamLine {
    private val deltaE = 2 * dy
    private val deltaNE = 2 * (dy - dx)
    private var d = 2 * dy - dx
    private var next: Int2D? = null

    init {
        while (next == null && x <= endX) {
            next = computeNext()
        }
    }

    override fun hasNext(): Boolean = next != null

    override fun next(): Int2D {
        val next = next ?: throw NoSuchElementException()
        this.next = if (x <= endX) computeNext() else null
        return next
    }

    private fun computeNext(): Int2D? {
        val next = converter(x++, y)
        if (d <= 0) {
            d += deltaE
        } else {
            y++
            d += deltaNE
        }
        return if (next in viewport) next else null
    }
}

private class DescendingFunky(
    private var x: Int,
    private var y: Int,
    private val endX: Int,
    dx: Int,
    dy: Int,
    private val viewport: Viewport,
    private val converter: (x: Int, y: Int) -> Int2D,
) : BresenhamLine {
    private val deltaE = 2 * dy
    private val deltaNE = 2 * (dy - dx)
    private var d = 2 * dy - dx
    private var next: Int2D? = null

    init {
        while (next == null && x >= endX) {
            next = computeNext()
        }
    }

    override fun hasNext(): Boolean = next != null

    override fun next(): Int2D {
        val next = next ?: throw NoSuchElementException()
        this.next = if (x >= endX) computeNext() else null
        return next
    }

    private fun computeNext(): Int2D? {
        val next = converter(x--, y)
        if (d > 0) {
            d += deltaE
        } else {
            y--
            d += deltaNE
        }
        return if (next in viewport) next else null
    }
}
