package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.CohenSutherlandOutCode
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.rasterizer.contains
import dev.staticsanches.kge.rasterizer.fittestX
import dev.staticsanches.kge.rasterizer.fittestY
import dev.staticsanches.kge.rasterizer.outCode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

interface BresenhamLine : Iterator<Int2D> {
    override fun next(): Int2D = processNext(::Int2D)

    fun <T> processNext(operation: (x: Int, y: Int) -> T): T
}

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
        return EmptyLine // outside viewport (trivial rejection)
    }

    val d = end - start
    return when {
        d == Int2D.zeroByZero -> SinglePointLine(start)
        d.x == 0 -> VerticalLine(start, end, viewport)
        d.y == 0 -> HorizontalLine(start, end, viewport)
        d.x == d.y || d.x == -d.y -> DiagonalLine(start, end, viewport)
        else -> funkyLine(start, end, d, viewport)
    }
}

private object EmptyLine : BresenhamLine {
    override fun hasNext(): Boolean = false

    override fun <T> processNext(operation: (Int, Int) -> T): T = throw NoSuchElementException()
}

private class SinglePointLine(
    private val next: Int2D,
) : BresenhamLine {
    private var hasNext = true

    override fun hasNext(): Boolean = hasNext

    override fun next(): Int2D {
        if (!hasNext) throw NoSuchElementException()
        hasNext = false
        return next
    }

    override fun <T> processNext(operation: (Int, Int) -> T): T {
        val (x, y) = next()
        return operation(x, y)
    }
}

private class VerticalLine private constructor(
    private val x: Int,
    private var y: Int,
    private val yInc: Int,
    private var dy: Int,
) : BresenhamLine {
    override fun hasNext(): Boolean = dy >= 0

    override fun <T> processNext(operation: (Int, Int) -> T): T {
        if (!hasNext()) throw NoSuchElementException()

        val nextY = y
        y += yInc
        dy--
        return operation(x, nextY)
    }

    companion object {
        operator fun invoke(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): VerticalLine {
            val x = start.x
            val y = viewport.fittestY(start)
            val dy = abs(viewport.fittestY(end) - y)
            val yInc = if (start.y < end.y) 1 else -1
            return VerticalLine(x, y, yInc, dy)
        }
    }
}

private class HorizontalLine private constructor(
    private var x: Int,
    private val y: Int,
    private val xInc: Int,
    private var dx: Int,
) : BresenhamLine {
    override fun hasNext(): Boolean = dx >= 0

    override fun <T> processNext(operation: (Int, Int) -> T): T {
        if (!hasNext()) throw NoSuchElementException()

        val nextX = x
        x += xInc
        dx--
        return operation(nextX, y)
    }

    companion object {
        operator fun invoke(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): HorizontalLine {
            val x = viewport.fittestX(start)
            val y = start.y
            val dx = abs(viewport.fittestX(end) - x)
            val xInc = if (start.x < end.x) 1 else -1
            return HorizontalLine(x, y, xInc, dx)
        }
    }
}

private class DiagonalLine private constructor(
    private val xInc: Int,
    private val yInc: Int,
    private var x: Int,
    private var y: Int,
    private var d: Int,
) : BresenhamLine {
    override fun hasNext(): Boolean = d >= 0

    override fun <T> processNext(operation: (Int, Int) -> T): T {
        if (!hasNext()) throw NoSuchElementException()

        val nextX = x
        val nextY = y
        x += xInc
        y += yInc
        d--
        return operation(nextX, nextY)
    }

    companion object {
        operator fun invoke(
            start: Int2D,
            end: Int2D,
            viewport: Viewport,
        ): DiagonalLine {
            val xInc = if (start.x < end.x) 1 else -1
            val yInc = if (start.y < end.y) 1 else -1
            var (x, y) = start
            val delta = max(abs(viewport.fittestX(start) - x), abs(viewport.fittestY(start) - y))
            x += (xInc * delta)
            y += (yInc * delta)
            val d = min(abs(viewport.fittestX(end) - x), abs(viewport.fittestY(end) - y))
            return DiagonalLine(xInc, yInc, x, y, d)
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
        AscendingFunkyLine(startX, startY, endX, dx, dy, viewport, converter)
    } else {
        DescendingFunkyLine(startX, startY, endX, dx, dy, viewport, converter)
    }
}

private class AscendingFunkyLine(
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

    override fun <T> processNext(operation: (Int, Int) -> T): T {
        val (x, y) = next()
        return operation(x, y)
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

private class DescendingFunkyLine(
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

    override fun <T> processNext(operation: (Int, Int) -> T): T {
        val (x, y) = next()
        return operation(x, y)
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
