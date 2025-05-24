package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.math.vector.MutableInt2D
import dev.staticsanches.kge.rasterizer.Viewport

interface ClipLineService : KGEExtensibleService {
    fun clipLine(
        viewport: Viewport.Bounded,
        p1: MutableInt2D,
        p2: MutableInt2D,
    ): Boolean

    companion object : ClipLineService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalClipLineServiceImplementation
}

val originalClipLineServiceImplementation: ClipLineService
    get() = DefaultClipLineService

data object DefaultClipLineService : ClipLineService {
    // https://en.wikipedia.org/wiki/Cohen%E2%80%93Sutherland_algorithm
    override fun clipLine(
        viewport: Viewport.Bounded,
        p1: MutableInt2D,
        p2: MutableInt2D,
    ): Boolean {
        val (minX, minY) = viewport.lowerBoundInclusive
        val (maxX, maxY) = viewport.upperBoundExclusive

        fun CSSegment(p: MutableInt2D): CSSegment {
            var outCode = INSIDE
            if (p.x < minX) {
                outCode = outCode or LEFT
            } else if (p.x > maxX) {
                outCode = outCode or RIGHT
            }
            if (p.y < minY) {
                outCode = outCode or BOTTOM
            } else if (p.y > maxY) {
                outCode = outCode or TOP
            }
            return outCode
        }

        var s1 = CSSegment(p1)
        var s2 = CSSegment(p2)
        while (true) {
            if (s1 or s2 == INSIDE) return true
            if (s1 and s2 != INSIDE) return false

            val s3 = if (s2 > s1) s2 else s1
            val n = MutableInt2D(0, 0)

            if (s3 and TOP != INSIDE) {
                n.x = p1.x + (p2.x - p1.x) * (maxY - p1.y) / (p2.y - p1.y)
                n.y = maxY
            } else if (s3 and BOTTOM != INSIDE) {
                n.x = p1.x + (p2.x - p1.x) * (minY - p1.y) / (p2.y - p1.y)
                n.y = minY
            } else if (s3 and RIGHT != INSIDE) {
                n.x = maxX
                n.y = p1.y + (p2.y - p1.y) * (maxX - p1.x) / (p2.x - p1.x)
            } else if (s3 and LEFT != INSIDE) {
                n.x = minX
                n.y = p1.y + (p2.y - p1.y) * (minX - p1.x) / (p2.x - p1.x)
            }

            if (s3 == s1) {
                p1.x = n.x
                p1.y = n.y
                s1 = CSSegment(p1)
            } else {
                p2.x = n.x
                p2.y = n.y
                s2 = CSSegment(p2)
            }
        }
        return true
    }

    const val INSIDE = 0b0000
    const val LEFT = 0b0001
    const val RIGHT = 0b0010
    const val BOTTOM = 0b0100
    const val TOP = 0b1000

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}

private typealias CSSegment = Int
