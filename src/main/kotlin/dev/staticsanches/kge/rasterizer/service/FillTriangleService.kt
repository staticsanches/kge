package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.pixelmap.PixelMap
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.MutableInt2D
import dev.staticsanches.kge.math.vector.mutableBy
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.rasterizer.utils.BresenhamLine
import dev.staticsanches.kge.rasterizer.utils.areCollinear
import dev.staticsanches.kge.rasterizer.utils.sortTriangleVertices
import dev.staticsanches.kge.spi.KGESPIExtensible
import dev.staticsanches.kge.utils.PeekingIterator
import dev.staticsanches.kge.utils.peeking

interface FillTriangleService : KGESPIExtensible {
    fun fillTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    )

    fun fillTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    )
}

internal object DefaultFillTriangleService : FillTriangleService {
    override fun fillTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) = innerFillTriangle(p0.toMutable(), p1.toMutable(), p2.toMutable(), color, target, pixelMode)

    override fun fillTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) = innerFillTriangle(x0 mutableBy y0, x1 mutableBy y1, x2 mutableBy y2, color, target, pixelMode)

    private fun innerFillTriangle(
        p0: MutableInt2D,
        p1: MutableInt2D,
        p2: MutableInt2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) {
        sortTriangleVertices(p0, p1, p2)

        if (areCollinear(p0, p1, p2)) {
            return Rasterizer.drawLine(p0, p2, color, target, pixelMode)
        }

        if (p0.y == p1.y) {
            return fillFlatTop(p0, p1, p2, color, false, target, pixelMode)
        }

        val leftResult: PeekingIterator<Int2D>
        val rightResult: PeekingIterator<Int2D>
        if (p1.x < p2.x) {
            leftResult = BresenhamLine(p0, p1, Viewport.Unbounded).iterator().peeking()
            rightResult = BresenhamLine(p0, p2, Viewport.Unbounded).iterator().peeking()
        } else {
            leftResult = BresenhamLine(p0, p2, Viewport.Unbounded).iterator().peeking()
            rightResult = BresenhamLine(p0, p1, Viewport.Unbounded).iterator().peeking()
        }

        while (leftResult.hasNext() && rightResult.hasNext()) {
            val nextLeft = leftResult.nextLeft()
            val nextRight = rightResult.nextRight()

            fillNext(nextLeft, nextRight, color, target, pixelMode)

            if (leftResult.hasNext() && !rightResult.hasNext() || !leftResult.hasNext() && rightResult.hasNext()) {
                return fillFlatTop(nextLeft, nextRight, p2, color, true, target, pixelMode)
            }
        }
    }

    private fun fillFlatTop(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        skipFirst: Boolean,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) {
        check(p0.y == p1.y)
        check(p0.x < p1.x)

        val leftResult = BresenhamLine(p0, p2, Viewport.Unbounded).iterator().peeking()
        val rightResult = BresenhamLine(p1, p2, Viewport.Unbounded).iterator().peeking()

        if (skipFirst && leftResult.hasNext() && rightResult.hasNext()) {
            leftResult.nextLeft()
            rightResult.nextRight()
        }

        while (leftResult.hasNext() && rightResult.hasNext()) {
            fillNext(leftResult.nextLeft(), rightResult.nextRight(), color, target, pixelMode)
        }
    }

    private fun fillNext(
        nextLeft: Int2D,
        nextRight: Int2D,
        color: Pixel,
        target: PixelMap,
        pixelMode: Pixel.Mode,
    ) {
        val y = nextLeft.y
        if (y !in 0..<target.height) return // out of bounds

        val width = target.width

        var minX = nextLeft.x
        if (minX >= width) {
            return // out of bounds
        } else if (minX < 0) {
            minX = 0
        }

        var maxX = nextRight.x
        if (maxX < 0) {
            return // out of bounds
        } else if (maxX >= width) {
            maxX = width - 1
        }

        for (x in minX..maxX) {
            Rasterizer.draw(x, y, color, target, pixelMode)
        }
    }

    private fun PeekingIterator<Int2D>.nextLeft(): Int2D {
        var next = next()
        while (hasNext() && peek().y == next.y) {
            val peek = next()
            if (peek.x < next.x) {
                next = peek
            }
        }
        return next
    }

    private fun PeekingIterator<Int2D>.nextRight(): Int2D {
        var next = next()
        while (hasNext() && peek().y == next.y) {
            val peek = next()
            if (peek.x > next.x) {
                next = peek
            }
        }
        return next
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
