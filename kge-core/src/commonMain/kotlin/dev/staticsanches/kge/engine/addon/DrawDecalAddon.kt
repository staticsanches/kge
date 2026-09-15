package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.engine.HasDrawModes
import dev.staticsanches.kge.engine.HasLayers
import dev.staticsanches.kge.engine.HasWindow
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.service.DrawDecalService

/**
 * Queues whole-decal instances on the target layer for the render step to
 * composite; the addon itself never draws.
 */
interface DrawDecalAddon :
    HasLayers,
    HasWindow,
    HasDrawModes {
    /**
     * Queues the whole [decal] with its top-left at [position], scaled by
     * [scale] and tinted uniformly by [tint], using the current decal modes and
     * the game screen as the viewport.
     */
    fun drawDecal(
        position: Float2D,
        decal: Decal,
        scale: Float2D = Float2D(1f, 1f),
        tint: Pixel = Colors.WHITE,
    ) {
        layers.target.decalInstances +=
            DrawDecalService.drawDecal(
                position = position,
                decal = decal,
                scale = scale,
                tint = tint,
                mode = decalMode,
                structure = decalStructure,
                viewport = window.screenSize,
            )
    }
}
