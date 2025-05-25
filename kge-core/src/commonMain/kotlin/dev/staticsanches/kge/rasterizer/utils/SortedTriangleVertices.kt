@file:Suppress("unused")

package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.math.vector.Int2D

class SortedTriangleVertices(
    p0: Int2D,
    p1: Int2D,
    p2: Int2D,
) {
    private val p0: Int2D
    private val p1: Int2D
    private val p2: Int2D

    init {
        var v0 = p0
        var v1 = p1
        var v2 = p2

        if (v0.y > v1.y) v0 = v1.also { v1 = v0 }
        if (v0.y > v2.y) v0 = v2.also { v2 = v0 }
        if (v1.y > v2.y) v1 = v2.also { v2 = v1 }

        if (v0.y == v1.y && v0.x > v1.x) v0 = v1.also { v1 = v0 } // flat top
        if (v0.y == v2.y && v0.x > v2.x) v0 = v2.also { v2 = v0 } // collinear
        if (v1.y == v2.y && v1.x > v2.x) v1 = v2.also { v2 = v1 } // flat bottom

        this.p0 = v0
        this.p1 = v1
        this.p2 = v2
    }

    val areCollinear: Boolean
        get() = p0.x * (p1.y - p2.y) + p1.x * (p2.y - p0.y) + p2.x * (p0.y - p1.y) == 0

    operator fun component1(): Int2D = p0

    operator fun component2(): Int2D = p1

    operator fun component3(): Int2D = p2
}
