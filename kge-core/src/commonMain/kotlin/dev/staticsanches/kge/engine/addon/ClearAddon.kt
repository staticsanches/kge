package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Pixel

/**
 * Fills the whole draw target, a no-op when it is null.
 *
 * `clear(pixel)` overwrites every cell; `clear(pixelByXY)` resolves each cell
 * from its coordinates; `clear(pixels)` consumes the iterable row-major,
 * leaving the cells it does not reach untouched.
 */
interface ClearAddon : HasDrawTarget {
    /** Overwrites every cell with [pixel]. */
    fun clear(pixel: Pixel) {
        (drawTarget ?: return).clear(pixel)
    }

    /** Replaces every cell with the value [pixelByXY] returns for its coordinates. */
    fun clear(pixelByXY: (x: Int, y: Int) -> Pixel) {
        val target = drawTarget ?: return
        for (y in 0 until target.height) {
            for (x in 0 until target.width) {
                target.uncheckedSet(x, y, pixelByXY(x, y))
            }
        }
    }

    /**
     * Overwrites the cells in row-major order with [pixels] until the iterable
     * is exhausted; the remaining cells keep their value.
     */
    fun clear(pixels: Iterable<Pixel>) {
        val target = drawTarget ?: return
        val iterator = pixels.iterator()
        for (y in 0 until target.height) {
            for (x in 0 until target.width) {
                if (!iterator.hasNext()) return
                target.uncheckedSet(x, y, iterator.next())
            }
        }
    }
}
