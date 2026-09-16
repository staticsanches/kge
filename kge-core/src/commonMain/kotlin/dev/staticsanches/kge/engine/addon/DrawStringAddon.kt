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
import dev.staticsanches.kge.text.DrawStringService

/**
 * The bitmap-text addon: olc's string surface over the run's font. The CPU
 * variants draw into [drawTarget]; the decal variants queue cells on the target
 * layer for the render step, using the game screen as the viewport.
 */
@OptIn(KGESensitiveAPI::class)
interface DrawStringAddon :
    HasDrawTarget,
    HasDrawModes,
    HasWindow,
    HasLayers,
    HasResourceScope {
    /** The number of 8px cells a tab advances; olc's `nTabSizeInSpaces` default. */
    val tabSizeInSpaces: Int get() = 4

    /** The pixel size of [text] in the mono font. */
    fun getTextSize(text: String): Int2D = DrawStringService.getTextSize(text, tabSizeInSpaces)

    /** The pixel size of [text] in the proportional font. */
    fun getTextSizeProp(text: String): Int2D = DrawStringService.getTextSizeProp(text, tabSizeInSpaces)

    /**
     * Draws [text] with the mono font at [position], or does nothing when there
     * is no draw target.
     */
    fun drawString(
        position: Int2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) = drawString(position.x, position.y, text, color, scale)

    /** The raw-coordinate form of [drawString]. */
    fun drawString(
        x: Int,
        y: Int,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        val target = drawTarget ?: return
        DrawStringService.drawString(resourceScope, target, x, y, text, color, scale, tabSizeInSpaces, pixelMode)
    }

    /**
     * Draws [text] with the proportional font at [position], or does nothing
     * when there is no draw target.
     */
    fun drawStringProp(
        position: Int2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) = drawStringProp(position.x, position.y, text, color, scale)

    /** The raw-coordinate form of [drawStringProp]. */
    fun drawStringProp(
        x: Int,
        y: Int,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Int = 1,
    ) {
        val target = drawTarget ?: return
        DrawStringService.drawStringProp(resourceScope, target, x, y, text, color, scale, tabSizeInSpaces, pixelMode)
    }

    /**
     * Queues [text]'s mono cells at [position] on the target layer, tinted by
     * [color] and scaled by [scale], with the game screen as the viewport.
     */
    fun drawStringDecal(
        position: Float2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D(1f, 1f),
    ) {
        DrawStringService.drawStringDecal(
            resourceScope,
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

    /**
     * Queues [text]'s proportional cells at [position] on the target layer,
     * tinted by [color] and scaled by [scale], with the game screen as the
     * viewport.
     */
    fun drawStringPropDecal(
        position: Float2D,
        text: String,
        color: Pixel = Colors.WHITE,
        scale: Float2D = Float2D(1f, 1f),
    ) {
        DrawStringService.drawStringPropDecal(
            resourceScope,
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
