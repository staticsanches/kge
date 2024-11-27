package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.SpriteDecal
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.FloatOneByOne
import dev.staticsanches.kge.math.vector.FloatZeroByZero
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.utils.invokeForAll

class LayerDescriptor private constructor(
    drawTarget: SpriteDecal,
    var offset: Float2D,
    var scale: Float2D,
    var show: Boolean,
    var update: Boolean,
    val decalInstances: MutableList<DecalInstance>,
    var tint: Pixel,
    var functionHook: ((LayerDescriptor) -> Unit)?,
) : KGEInternalResource {
    var drawTarget: SpriteDecal = drawTarget
        private set

    fun resize(
        width: Int,
        height: Int,
    ) {
        close() // free the previous target
        drawTarget = SpriteDecal(width, height)
        update = true
    }

    @KGESensitiveAPI
    override fun close() = invokeForAll(drawTarget.sprite, drawTarget) { it.close() }

    companion object {
        operator fun invoke(
            window: Window,
            width: Int,
            height: Int,
            offset: Float2D = FloatZeroByZero,
            scale: Float2D = FloatOneByOne,
            show: Boolean = false,
            update: Boolean = false,
            decalInstances: MutableList<DecalInstance> = mutableListOf(),
            tint: Pixel = Colors.WHITE,
            functionHook: ((LayerDescriptor) -> Unit)? = null,
        ): LayerDescriptor =
            LayerDescriptor(
                SpriteDecal(width, height),
                offset,
                scale,
                show,
                update,
                decalInstances,
                tint,
                functionHook,
            ).apply { window.bindResource(this) }

        private fun SpriteDecal(
            width: Int,
            height: Int,
        ): SpriteDecal = Sprite.create(width, height).closeIfFailed { SpriteDecal(it, filtered = false, clamp = true) }
    }
}
