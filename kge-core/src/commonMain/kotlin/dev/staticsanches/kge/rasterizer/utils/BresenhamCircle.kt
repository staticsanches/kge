@file:Suppress("unused")

package dev.staticsanches.kge.rasterizer.utils

import kotlin.jvm.JvmInline

/**
 * Uses [Bresenham's circle algorithm](https://www.geeksforgeeks.org/bresenhams-circle-drawing-algorithm/) to
 * generate points of the first octant of a circle centered (0, [radius]).
 */
inline fun bresenhamCircle(
    radius: Int,
    mask: CircleOctantMask,
    drawPixels: (
        x: Int,
        y: Int,
        o1: Boolean,
        o2: Boolean,
        o3: Boolean,
        o4: Boolean,
        o5: Boolean,
        o6: Boolean,
        o7: Boolean,
        o8: Boolean,
    ) -> Unit,
) {
    if (radius <= 0 || mask == CircleOctantMask.NONE) return // none to be drawn

    // Octant mask
    val o1 = mask intersects CircleOctantMask.O1
    val o2 = mask intersects CircleOctantMask.O2
    val o3 = mask intersects CircleOctantMask.O3
    val o4 = mask intersects CircleOctantMask.O4
    val o5 = mask intersects CircleOctantMask.O5
    val o6 = mask intersects CircleOctantMask.O6
    val o7 = mask intersects CircleOctantMask.O7
    val o8 = mask intersects CircleOctantMask.O8

    var d = 3 - 2 * radius
    var x = 0
    var y = radius
    while (x <= y) {
        drawPixels(x, y, o1, o2, o3, o4, o5, o6, o7, o8)
        d += if (d > 0) 4 * (x++ - y--) + 10 else 4 * x++ + 6
    }
}

/**
 * @see <img width="400" src="https://ars.els-cdn.com/content/image/1-s2.0-S0166218X07004817-gr2.jpg" />
 */
@JvmInline
value class CircleOctantMask private constructor(
    private val mask: Int,
) {
    infix fun or(other: CircleOctantMask): CircleOctantMask = CircleOctantMask(mask or other.mask)

    infix fun and(other: CircleOctantMask): CircleOctantMask = CircleOctantMask(mask and other.mask)

    infix fun xor(other: CircleOctantMask): CircleOctantMask = CircleOctantMask(mask xor other.mask)

    infix fun intersects(other: CircleOctantMask): Boolean = mask and other.mask != 0

    override fun toString(): String = mask.toString(2).padStart(8, '0')

    companion object {
        val O1 = CircleOctantMask(0b1000_0000)
        val O2 = CircleOctantMask(0b0100_0000)
        val O3 = CircleOctantMask(0b0010_0000)
        val O4 = CircleOctantMask(0b0001_0000)
        val O5 = CircleOctantMask(0b0000_1000)
        val O6 = CircleOctantMask(0b0000_0100)
        val O7 = CircleOctantMask(0b0000_0010)
        val O8 = CircleOctantMask(0b0000_0001)

        val ALL = CircleOctantMask(0b1111_1111)
        val NONE = CircleOctantMask(0b0000_0000)
    }
}
