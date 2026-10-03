package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D

/**
 * The TrueType text addon: draws a loaded [Font]'s text into the engine's draw
 * target, with olc's string defaults.
 */
@OptIn(KGESensitiveAPI::class)
interface TtfDrawStringAddon :
    HasDrawTarget,
    HasDrawModes,
    HasWindow,
    HasLayers {
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

    /**
     * Queues one partial-decal instance per ink glyph of [text] at [position],
     * tinted by [color] and scaled by [scale], for the render step to flush.
     */
    fun drawStringDecal(
        font: Font,
        position: Float2D,
        text: String,
        sizePx: Int,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D(1f, 1f),
    ) {
        TtfTextService.drawStringDecal(
            font, position, text, sizePx, color, scale, tabSizeInSpaces,
            window.screenSize, decalMode, decalStructure,
            layers.target.decalInstances::add,
        )
    }
}
