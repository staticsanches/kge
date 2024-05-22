package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.MutableInt2D

/**
 * Uses the [triangle area](https://www.geeksforgeeks.org/area-of-triangle-using-determinants/) to check if the
 * points are collinear.
 */
fun areCollinear(
    p0: Int2D,
    p1: Int2D,
    p2: Int2D,
): Boolean = p0.x * (p1.y - p2.y) + p1.x * (p2.y - p0.y) + p2.x * (p0.y - p1.y) == 0

/**
 * Ensure p0.y <= p1.y <= p2.y, and that flat triangles have ordered x-coordinates.
 */
fun sortTriangleVertices(
    p0: MutableInt2D,
    p1: MutableInt2D,
    p2: MutableInt2D,
) {
    if (p0.y > p1.y) p0 swappedWith p1
    if (p0.y > p2.y) p0 swappedWith p2
    if (p1.y > p2.y) p1 swappedWith p2

    if (p0.y == p1.y && p0.x > p1.x) p0 swappedWith p1 // flat top
    if (p0.y == p2.y && p0.x > p2.x) p0 swappedWith p2 // collinear
    if (p1.y == p2.y && p1.x > p2.x) p1 swappedWith p2 // flat bottom
}
