package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.rasterizer.CohenSutherlandOutCode
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.types.vector.Int2D
import dev.staticsanches.kge.types.vector.IntZeroByZero
import dev.staticsanches.kge.types.vector.by
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

typealias BresenhamLine = Iterable<Int2D>

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
        return emptyList() // outside viewport (trivial rejection)
    }

    val d = end - start
    return when {
        d == IntZeroByZero -> listOf(start)
        d.x == 0 -> Vertical.line(start, end, viewport)
        d.y == 0 -> Horizontal.line(start, end, viewport)
        d.x == d.y || d.x == -d.y -> Diagonal.line(start, end, viewport)
        else -> Funky.line(start, end, viewport)
    }
}

private class Vertical private constructor(
    private val x: Int,
    private var y: Int,
    private val yInc: Int,
    private var dy: Int,
) : Iterator<Int2D> {
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
            return Iterable { Vertical(x, y, yInc, dy) }
        }
    }
}

private class Horizontal private constructor(
    private var x: Int,
    private val y: Int,
    private val xInc: Int,
    private var dx: Int,
) : Iterator<Int2D> {
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
            return Iterable { Horizontal(x, y, xInc, dx) }
        }
    }
}

private class Diagonal private constructor(
    private val xInc: Int,
    private val yInc: Int,
    private var x: Int,
    private var y: Int,
    private var d: Int,
) : Iterator<Int2D> {
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
            val (x, y) = computeStart(start, viewport, xInc, yInc)
            val d = min(abs(viewport.fittestX(end) - x), abs(viewport.fittestY(end) - y))
            return Iterable { Diagonal(xInc, yInc, x, y, d) }
        }

        private fun computeStart(
            start: Int2D,
            viewport: Viewport,
            xInc: Int,
            yInc: Int,
        ): Int2D {
            var (x, y) = start
            val delta = max(abs(viewport.fittestX(start) - x), abs(viewport.fittestY(start) - y))
            x += (xInc * delta)
            y += (yInc * delta)
            return x by y
        }
    }
}

private class Funky private constructor(
    var x: Int,
    var y: Int,
    private val endX: Int,
    dx: Int,
    dy: Int,
    private val viewport: Viewport,
    private val converter: (x: Int, y: Int) -> Int2D,
) : Iterator<Int2D> {
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
        if (next in viewport) {
            return next
        }
        return null
    }

    companion object {
        fun line(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): BresenhamLine = line(start.x, start.y, end.x, end.y, viewport, false, ::Int2D)

        private fun line(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            viewport: Viewport,
            mustReverse: Boolean,
            converter: (x: Int, y: Int) -> Int2D,
        ): BresenhamLine {
            if (startX > endX) { // ensure starX <= endX
                return line(endX, endY, startX, startY, viewport, !mustReverse, converter)
            }

            val dy = endY - startY
            if (dy < 0) { // slope < 0: reflect on X-axis
                return line(startX, -startY, endX, -endY, viewport, mustReverse) { x, y -> converter(x, -y) }
            }

            val dx = endX - startX
            if (dy > dx) { // slope > 1: swap x and y
                return line(startY, startX, endY, endX, viewport, mustReverse) { x, y -> converter(y, x) }
            }

            val line = Iterable { Funky(startX, startY, endX, dx, dy, viewport, converter) }
            if (!mustReverse) {
                return line
            }

            val reversed = LinkedList<Int2D>()
            line.forEach { reversed.addFirst(it) }
            return reversed
        }
    }
}
