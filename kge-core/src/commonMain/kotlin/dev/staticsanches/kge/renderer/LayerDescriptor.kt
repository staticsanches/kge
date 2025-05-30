@file:Suppress("unused")

package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.image.extension.create
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.letClosingIfFailed
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
        operator fun invoke(
            window: Window,
            width: Int,
            height: Int,
            offset: Float2D = Float2D.zeroByZero,
            scale: Float2D = Float2D.oneByOne,
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
            ).apply { window.bindResource(this) }

        private fun Decal(
            width: Int,
            height: Int,
        ): Decal =
            Sprite
                .create(width, height, color = Colors.BLANK)
                .letClosingIfFailed { Decal(it, filtered = false, clamp = true) }
    }
}
