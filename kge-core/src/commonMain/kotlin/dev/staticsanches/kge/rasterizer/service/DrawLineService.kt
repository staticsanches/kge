@file:Suppress("unused")

package dev.staticsanches.kge.rasterizer.service

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.MutablePixelMap
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.rasterizer.utils.BresenhamLine

interface DrawLineService : KGEExtensibleService {
    fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel,
        pattern: LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    )

    sealed interface LinePattern {
        fun shouldDrawPixel(): Boolean

        data object Empty : LinePattern {
            override fun shouldDrawPixel(): Boolean = false
        }

        data object Filled : LinePattern {
            override fun shouldDrawPixel(): Boolean = true
        }

        class Dotted(
            private var current: Boolean = true,
        ) : LinePattern {
            override fun shouldDrawPixel(): Boolean {
                val next = current
                current = !current
                return next
            }
        }

        interface Custom : LinePattern
    }

    companion object : DrawLineService by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalDrawLineServiceImplementation
}

val originalDrawLineServiceImplementation: DrawLineService
    get() = DefaultDrawLineService

private data object DefaultDrawLineService : DrawLineService {
    override val servicePriority: Int
        get() = Int.MIN_VALUE

    override fun drawLine(
        start: Int2D,
        end: Int2D,
        color: Pixel,
        pattern: DrawLineService.LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) {
        if (pattern == DrawLineService.LinePattern.Empty) return

        val draw: (Int, Int) -> Unit =
            if (pattern == DrawLineService.LinePattern.Filled) {
                { x, y -> Rasterizer.draw(x, y, color, target, pixelMode) }
            } else {
                { x, y ->
                    if (pattern.shouldDrawPixel()) Rasterizer.draw(x, y, color, target, pixelMode)
                }
            }
        with(BresenhamLine(start, end, target)) {
            while (hasNext()) {
                processNext(draw)
            }
        }
    }

    override fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: Pixel,
        pattern: DrawLineService.LinePattern,
        target: MutablePixelMap,
        pixelMode: Pixel.Mode,
    ) = drawLine(
        start = startX by startY,
        end = endX by endY,
        color = color,
        pattern = pattern,
        target = target,
        pixelMode = pixelMode,
    )
}
