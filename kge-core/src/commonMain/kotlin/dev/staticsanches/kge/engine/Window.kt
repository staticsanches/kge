package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.state.DimensionState
import dev.staticsanches.kge.engine.state.TimeState
import dev.staticsanches.kge.engine.state.WithKGEState
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.LayerDescriptor
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.utils.invokeForAllRemoving
import kotlin.getValue

class Window
    @KGESensitiveAPI
    constructor(
        private val mainResourceWrapper: ResourceWrapper<WindowMainResource>,
    ) : WithKGEState,
        KGEInternalResource {
        private val boundResources = mutableListOf<KGEResource>()

        init {
            bindResource(mainResourceWrapper)
        }

        @KGESensitiveAPI
        override val mainResource: WindowMainResource by mainResourceWrapper::resource

        override val dimensionState: DimensionState = DimensionState()
        override val screenSize: Int2D by dimensionState::screenSize
        override val invertedScreenSize: Float2D by dimensionState::invertedScreenSize

        override val timeState: TimeState = TimeState()

        override var decalMode = Decal.Mode.NORMAL
        override var pixelMode: Pixel.Mode = Pixel.Mode.Normal
        override var decalStructure = Decal.Structure.FAN
        override var suspendTextureTransfer = false

        @KGESensitiveAPI
        override val layers = ArrayList<LayerDescriptor>()

        @KGESensitiveAPI
        override var targetLayerIndex = 0

        override var drawTarget: Sprite? = null
            @KGESensitiveAPI
            set(value) {
                if (value == null) {
                    targetLayerIndex = 0
                    field = layers[0].drawTarget.sprite
                } else {
                    field = value
                }
            }

        @KGESensitiveAPI
        fun bindResource(resource: KGEResource) = boundResources.add(0, resource)

        @KGESensitiveAPI
        override fun close() = boundResources.invokeForAllRemoving(KGEResource::close)
    }

expect class WindowMainResource
