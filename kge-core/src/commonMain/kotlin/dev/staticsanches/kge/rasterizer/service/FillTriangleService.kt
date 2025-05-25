package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.rasterizer.utils.BresenhamLine
import dev.staticsanches.kge.rasterizer.utils.SortedTriangleVertices
import dev.staticsanches.kge.utils.PeekingIterator
import dev.staticsanches.kge.utils.peeking

interface FillTriangleService : KGEExtensibleService {
    fun fillTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: MutablePixelMap,
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
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    companion object : FillTriangleService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalFillTriangleServiceImplementation
}

private val originalFillTriangleServiceImplementation: FillTriangleService
    get() = DefaultFillTriangleService

private data object DefaultFillTriangleService : FillTriangleService {
    override fun fillTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = innerFillTriangle(SortedTriangleVertices(p0, p1, p2), color, target, pixelMode)

    override fun fillTriangle(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = innerFillTriangle(SortedTriangleVertices(x0 by y0, x1 by y1, x2 by y2), color, target, pixelMode)

    private fun innerFillTriangle(
        vertices: SortedTriangleVertices,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        val (p0, p1, p2) = vertices

        if (vertices.areCollinear) {
            return Rasterizer.drawLine(p0, p2, color, DrawLineService.LinePattern.Filled, target, pixelMode)
        }

        if (p0.y == p1.y) {
            return fillFlatTop(p0, p1, p2, color, false, target, pixelMode)
        }

        val leftResult: PeekingIterator<Int2D>
        val rightResult: PeekingIterator<Int2D>
        if (p1.x < p2.x) {
            leftResult = BresenhamLine(p0, p1, Viewport.Unbounded).peeking()
            rightResult = BresenhamLine(p0, p2, Viewport.Unbounded).peeking()
        } else {
            leftResult = BresenhamLine(p0, p2, Viewport.Unbounded).peeking()
            rightResult = BresenhamLine(p0, p1, Viewport.Unbounded).peeking()
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
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        check(p0.y == p1.y)
        check(p0.x < p1.x)

        val leftResult = BresenhamLine(p0, p2, Viewport.Unbounded).peeking()
        val rightResult = BresenhamLine(p1, p2, Viewport.Unbounded).peeking()

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
        target: MutablePixelMap,
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
