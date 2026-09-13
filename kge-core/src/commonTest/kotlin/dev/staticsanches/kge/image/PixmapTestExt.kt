package dev.staticsanches.kge.image

/**
 * A row-major [Sequence] of this surface's pixels, for test assertions only.
 */
internal fun Pixmap.asSequence(): Sequence<Pixel> =
    sequence {
        for (y in 0 until height) {
            for (x in 0 until width) {
                yield(uncheckedGet(x, y))
            }
        }
    }
