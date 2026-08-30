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
import kotlin.math.max
import kotlin.math.min

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
            return fillFlatTop(
                topRowY = p0.y,
                leftTopX = p0.x,
                rightTopX = p1.x,
                bottomVertex = p2,
                color = color,
                skipFirst = false,
                target = target,
                pixelMode = pixelMode,
            )
        }

        val leftWalker: SideWalker
        val rightWalker: SideWalker
        if (p1.x < p2.x) {
            leftWalker = SideWalker(BresenhamLine(p0, p1, Viewport.Unbounded))
            rightWalker = SideWalker(BresenhamLine(p0, p2, Viewport.Unbounded))
        } else {
            leftWalker = SideWalker(BresenhamLine(p0, p2, Viewport.Unbounded))
            rightWalker = SideWalker(BresenhamLine(p0, p1, Viewport.Unbounded))
        }

        var rowY = p0.y
        while (leftWalker.hasMore() && rightWalker.hasMore()) {
            val minX = leftWalker.consumeRow(rowY, isLeft = true) ?: break
            val maxX = rightWalker.consumeRow(rowY, isLeft = false) ?: break

            fillNext(minX, maxX, rowY, color, target, pixelMode)
            rowY++

            if (!leftWalker.hasMore() || !rightWalker.hasMore()) {
                return fillFlatTop(
                    topRowY = rowY - 1,
                    leftTopX = minX,
                    rightTopX = maxX,
                    bottomVertex = p2,
                    color = color,
                    skipFirst = true,
                    target = target,
                    pixelMode = pixelMode,
                )
            }
        }
    }

    private fun fillFlatTop(
        topRowY: Int,
        leftTopX: Int,
        rightTopX: Int,
        bottomVertex: Int2D,
        color: Pixel,
        skipFirst: Boolean,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        check(leftTopX < rightTopX)

        val leftWalker = SideWalker(BresenhamLine(leftTopX by topRowY, bottomVertex, Viewport.Unbounded))
        val rightWalker = SideWalker(BresenhamLine(rightTopX by topRowY, bottomVertex, Viewport.Unbounded))

        var rowY = topRowY
        if (skipFirst) {
            // The first row was already filled by the caller
            leftWalker.consumeRow(topRowY, isLeft = true)
            rightWalker.consumeRow(topRowY, isLeft = false)
            rowY++
        }

        while (leftWalker.hasMore() && rightWalker.hasMore()) {
            val minX = leftWalker.consumeRow(rowY, isLeft = true) ?: break
            val maxX = rightWalker.consumeRow(rowY, isLeft = false) ?: break

            fillNext(minX, maxX, rowY, color, target, pixelMode)
            rowY++
        }
    }

    private fun fillNext(
        minX: Int,
        maxX: Int,
        y: Int,
        color: Pixel,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (y !in 0..<target.height) return // out of bounds

        val width = target.width

        var clippedMinX = minX
        if (clippedMinX >= width) {
            return // out of bounds
        } else if (clippedMinX < 0) {
            clippedMinX = 0
        }

        var clippedMaxX = maxX
        if (clippedMaxX < 0) {
            return // out of bounds
        } else if (clippedMaxX >= width) {
            clippedMaxX = width - 1
        }

        Rasterizer.drawSpan(clippedMinX, clippedMaxX, y, color, target, pixelMode)
    }

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

/**
 * Walks one side of a triangle without allocating [Int2D]s: it consumes the points of the
 * [BresenhamLine] through [BresenhamLine.processNext] and groups them by row.
 */
private class SideWalker(
    private val line: BresenhamLine,
) {
    private var pendingX: Int = 0
    private var pendingY: Int = 0
    private var hasPending: Boolean = false

    fun hasMore(): Boolean = hasPending || line.hasNext()

    /**
     * Consumes all the points of this side whose y equals [rowY] and returns the extreme x
     * (minimum for a left side, maximum for a right side), or null when the side has no point in
     * that row (i.e. it has already ended).
     */
    fun consumeRow(
        rowY: Int,
        isLeft: Boolean,
    ): Int? {
        if (!loadNext() || pendingY != rowY) return null

        var extreme = pendingX
        hasPending = false
        while (loadNext() && pendingY == rowY) {
            extreme = if (isLeft) min(extreme, pendingX) else max(extreme, pendingX)
            hasPending = false
        }
        return extreme
    }

    private fun loadNext(): Boolean {
        if (hasPending) return true
        if (!line.hasNext()) return false
        line.processNext { x, y ->
            pendingX = x
            pendingY = y
        }
        hasPending = true
        return true
    }
}
