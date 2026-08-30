package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.image.Colors.BLANK
import dev.staticsanches.kge.image.Colors.RED
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.rasterizer.utils.BresenhamLine
import dev.staticsanches.kge.rasterizer.utils.SortedTriangleVertices
import dev.staticsanches.kge.utils.PeekingIterator
import dev.staticsanches.kge.utils.peeking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity tests for [Rasterizer.fillTriangle]: [referenceFillTriangle] is a faithful copy of the
 * previous scanline implementation (peeking iterators + per-pixel draw) and is compared pixel by
 * pixel against the current optimized implementation.
 */
class FillTriangleServiceTest {
    // Covers: generic, flat top, flat bottom, inverted (middle vertex on the right), vertical
    // left edge, degenerate (single point), collinear (draws a line), partially outside the
    // viewport and fully outside the viewport.
    private val triangles =
        listOf(
            Triple(0 by 2, 3 by 0, 6 by 4),
            Triple(1 by 0, 5 by 0, 3 by 4),
            Triple(1 by 4, 5 by 4, 3 by 0),
            Triple(4 by 0, 0 by 3, 7 by 5),
            Triple(3 by 0, 3 by 5, 7 by 3),
            Triple(2 by 1, 4 by 1, 2 by 1),
            Triple(0 by 0, 4 by 2, 8 by 4),
            Triple(5 by 5, 15 by 8, -5 by 12),
            Triple(-5 by -5, -2 by -2, -8 by -1),
        )

    @Test
    fun shouldMatchReferenceRasterizer() {
        for (triangle in triangles) {
            assertMatchesReference(triangle, Pixel.Mode.Normal)
        }
    }

    @Test
    fun shouldMatchReferenceRasterizerInAlphaMode() {
        for (triangle in triangles) {
            assertMatchesReference(triangle, Pixel.Mode.Alpha(0.5f))
        }
    }

    private fun assertMatchesReference(
        triangle: Triple<Int2D, Int2D, Int2D>,
        pixelMode: Pixel.Mode,
    ) {
        val (p0, p1, p2) = triangle

        Sprite.create(10, 10, color = BLANK).use { expected ->
            referenceFillTriangle(p0, p1, p2, RED, expected, pixelMode)

            Sprite.create(10, 10, color = BLANK).use { actual ->
                Rasterizer.fillTriangle(p0, p1, p2, RED, actual, pixelMode)
                assertEquals(expected.toList(), actual.toList(), "triangle: $triangle, mode: $pixelMode")
            }
        }
    }

    private fun referenceFillTriangle(
        p0: Int2D,
        p1: Int2D,
        p2: Int2D,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        val vertices = SortedTriangleVertices(p0, p1, p2)
        val (sp0, sp1, sp2) = vertices

        if (vertices.areCollinear) {
            return Rasterizer.drawLine(sp0, sp2, color, DrawLineService.LinePattern.Filled, target, pixelMode)
        }

        if (sp0.y == sp1.y) {
            return referenceFlatTop(sp0, sp1, sp2, color, skipFirst = false, target, pixelMode)
        }

        val leftIterator: PeekingIterator<Int2D>
        val rightIterator: PeekingIterator<Int2D>
        if (sp1.x < sp2.x) {
            leftIterator = BresenhamLine(sp0, sp1, Viewport.Unbounded).peeking()
            rightIterator = BresenhamLine(sp0, sp2, Viewport.Unbounded).peeking()
        } else {
            leftIterator = BresenhamLine(sp0, sp2, Viewport.Unbounded).peeking()
            rightIterator = BresenhamLine(sp0, sp1, Viewport.Unbounded).peeking()
        }

        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            val nextLeft = leftIterator.referenceNextLeft()
            val nextRight = rightIterator.referenceNextRight()

            referenceFillNext(nextLeft, nextRight, color, target, pixelMode)

            val eitherSideEnded =
                leftIterator.hasNext() && !rightIterator.hasNext() ||
                    !leftIterator.hasNext() && rightIterator.hasNext()
            if (eitherSideEnded) {
                return referenceFlatTop(nextLeft, nextRight, sp2, color, skipFirst = true, target, pixelMode)
            }
        }
    }

    private fun referenceFlatTop(
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

        val leftIterator = BresenhamLine(p0, p2, Viewport.Unbounded).peeking()
        val rightIterator = BresenhamLine(p1, p2, Viewport.Unbounded).peeking()

        if (skipFirst && leftIterator.hasNext() && rightIterator.hasNext()) {
            leftIterator.referenceNextLeft()
            rightIterator.referenceNextRight()
        }

        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            referenceFillNext(
                leftIterator.referenceNextLeft(),
                rightIterator.referenceNextRight(),
                color, target, pixelMode,
            )
        }
    }

    private fun referenceFillNext(
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

    private fun PeekingIterator<Int2D>.referenceNextLeft(): Int2D {
        var next = next()
        while (hasNext() && peek().y == next.y) {
            val peek = next()
            if (peek.x < next.x) {
                next = peek
            }
        }
        return next
    }

    private fun PeekingIterator<Int2D>.referenceNextRight(): Int2D {
        var next = next()
        while (hasNext() && peek().y == next.y) {
            val peek = next()
            if (peek.x > next.x) {
                next = peek
            }
        }
        return next
    }
}
