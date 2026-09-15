package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.service.DrawPartialDecalService

/**
 * Queues partial-decal instances on the target layer for the render step to
 * composite; the addon itself never draws.
 */
interface DrawPartialDecalAddon :
    HasLayers,
    HasWindow,
    HasDrawModes {
    /**
     * Queues the [sourceSize]-sized region at [sourcePosition] of [decal] with
     * its top-left at [position], scaled by [scale] and tinted uniformly by
     * [tint], using the current decal modes and the game screen as the viewport.
     */
    fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D = Float2D(1f, 1f),
        tint: Pixel = Colors.WHITE,
    ) {
        layers.target.decalInstances +=
            DrawPartialDecalService.drawPartialDecal(
                position = position,
                decal = decal,
                sourcePosition = sourcePosition,
                sourceSize = sourceSize,
                scale = scale,
                tint = tint,
                mode = decalMode,
                structure = decalStructure,
                viewport = window.screenSize,
            )
    }
}
