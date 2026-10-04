package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasDrawTarget
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasResourceScope
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.text.KGEFont

/**
 * The engine-facing text surface: the host's principal font and tab size drive
 * measure, CPU and decal draws, and one call may name a different font.
 */
@OptIn(KGESensitiveAPI::class)
interface TextAddon :
    HasDrawTarget,
    HasDrawModes,
    HasWindow,
    HasLayers,
    HasResourceScope {
    /** The principal font of subsequent calls; a call may name another one. */
    var textFont: KGEFont

    /** The spaces a tab stop spans; positive. */
    var tabSizeInSpaces: Int

    /** The pixel box of [text] under [font]. */
    fun measureText(
        text: String,
        font: KGEFont = textFont,
    ): Int2D = font.measureText(text, tabSizeInSpaces)

    /** Draws [text] at [position] under [font]; no draw target is a no-op. */
    fun drawText(
        position: Int2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
        font: KGEFont = textFont,
    ) = drawText(position.x, position.y, text, color, scale, font)

    /** The raw-coordinate form of [drawText]. */
    fun drawText(
        x: Int,
        y: Int,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
        font: KGEFont = textFont,
    ) {
        val target = drawTarget ?: return
        font.drawText(target, x, y, text, color, scale, tabSizeInSpaces, pixelMode)
    }

    /** Queues [text]'s cells at [position] on the target layer, with the game screen as the viewport. */
    fun drawTextDecal(
        position: Float2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D(1f, 1f),
        font: KGEFont = textFont,
    ) {
        font.drawTextDecal(
            position,
            text,
            color,
            scale,
            tabSizeInSpaces,
            window.screenSize,
            decalMode,
            decalStructure,
            layers.target.decalInstances::add,
        )
    }
}
