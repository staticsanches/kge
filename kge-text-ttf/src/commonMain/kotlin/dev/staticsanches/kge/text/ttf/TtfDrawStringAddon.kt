package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Int2D

/**
 * The TrueType text addon: draws a loaded [Font]'s text into the engine's draw
 * target, with olc's string defaults.
 */
interface TtfDrawStringAddon :
    HasDrawTarget,
    HasDrawModes {
    /** The number of space advances a tab stop spans; olc's `nTabSizeInSpaces`. */
    val tabSizeInSpaces: Int get() = 4

    /** The pixel box of [text] at [sizePx] with this addon's tab stops. */
    fun getTextSize(
        font: Font,
        text: String,
        sizePx: Int,
    ): Int2D = TtfTextService.getTextSize(font, text, sizePx, tabSizeInSpaces)

    /** Draws [text] at [position], or does nothing when there is no draw target. */
    fun drawString(
        font: Font,
        position: Int2D,
        text: String,
        sizePx: Int,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) = drawString(font, position.x, position.y, text, sizePx, color, scale)

    /** The raw-coordinate form of [drawString]. */
    fun drawString(
        font: Font,
        x: Int,
        y: Int,
        text: String,
        sizePx: Int,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        val target = drawTarget ?: return
        TtfTextService.drawString(font, target, x, y, text, sizePx, color, scale, tabSizeInSpaces, pixelMode)
    }
}
