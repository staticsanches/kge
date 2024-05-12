package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.closeIfFailed
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.FloatOneByOne
import dev.staticsanches.kge.types.vector.FloatZeroByZero
import dev.staticsanches.kge.utils.invokeForAll

class LayerDescriptor private constructor(
    drawTarget: Decal,
    var offset: Float2D,
    var scale: Float2D,
    var show: Boolean,
    var update: Boolean,
    val decalInstances: MutableList<DecalInstance>,
    var tint: Pixel,
    var functionHook: ((LayerDescriptor) -> Unit)?,
) : KGEInternalResource {
    var drawTarget: Decal = drawTarget
        private set

    context(Window)
    fun resize(
        width: Int,
        height: Int,
    ) {
        close() // free the previous target
        drawTarget = Decal(width, height)
        update = true
    }

    @KGESensitiveAPI
    override fun close() = invokeForAll(drawTarget.sprite, drawTarget) { it.close() }

    companion object {
        context(Window)
        operator fun invoke(
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
                Decal(width, height),
                offset,
                scale,
                show,
                update,
                decalInstances,
                tint,
                functionHook,
            ).apply { bindResource(this) }

        context(Window)
        private fun Decal(
            width: Int,
            height: Int,
        ): Decal = Sprite.create(width, height).closeIfFailed { Decal(it, filtered = false, clamp = true) }
    }
}
