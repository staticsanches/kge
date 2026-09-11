package dev.staticsanches.kge.rasterizer

import kotlin.jvm.JvmInline

/**
 * A selection of the eight octants of a circle, oriented clockwise from the
 * top: [O1] is top→NE, [O2] NE→E, [O3] E→SE, [O4] SE→S, [O5] S→SW, [O6]
 * SW→W, [O7] W→NW and [O8] NW→N. The bits run from [O1] at the MSB
 * (`0b1000_0000`) to [O8] at the LSB (`0b0000_0001`); [ALL] is the full
 * selection and [NONE] the empty one.
 *
 * A cell exactly on an octant boundary — an axis or a diagonal — belongs to
 * the odd octant on that boundary: the top axis and the NE diagonal to [O1],
 * the east axis and the SE diagonal to [O3], the south axis and the SW
 * diagonal to [O5], the west axis and the NW diagonal to [O7]. The center is
 * the shared apex, so any non-[NONE] selection paints it.
 */
@JvmInline
value class CircleOctantMask private constructor(
    private val mask: Int,
) {
    /** The union of this selection and [other]. */
    infix fun or(other: CircleOctantMask): CircleOctantMask = CircleOctantMask(mask or other.mask)

    /** Whether this selection and [other] share at least one octant. */
    infix fun intersects(other: CircleOctantMask): Boolean = (mask and other.mask) != 0

    companion object {
        /** The top→NE octant (MSB). */
        val O1 = CircleOctantMask(0b1000_0000)

        /** The NE→E octant. */
        val O2 = CircleOctantMask(0b0100_0000)

        /** The E→SE octant. */
        val O3 = CircleOctantMask(0b0010_0000)

        /** The SE→S octant. */
        val O4 = CircleOctantMask(0b0001_0000)

        /** The S→SW octant. */
        val O5 = CircleOctantMask(0b0000_1000)

        /** The SW→W octant. */
        val O6 = CircleOctantMask(0b0000_0100)

        /** The W→NW octant. */
        val O7 = CircleOctantMask(0b0000_0010)

        /** The NW→N octant (LSB). */
        val O8 = CircleOctantMask(0b0000_0001)

        /** Every octant. */
        val ALL = CircleOctantMask(0b1111_1111)

        /** No octant. */
        val NONE = CircleOctantMask(0b0000_0000)
    }
}
