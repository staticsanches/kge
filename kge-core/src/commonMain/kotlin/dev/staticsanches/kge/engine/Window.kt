package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.configuration.KGEConfiguration
import dev.staticsanches.kge.engine.state.DimensionState
import dev.staticsanches.kge.engine.state.TimeState
import dev.staticsanches.kge.engine.state.WithKGEState
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.renderer.LayerDescriptor
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.utils.invokeForAllRemoving
import kotlin.getValue

class Window
    @KGESensitiveAPI
    constructor(
        mainResourceWrapper: ResourceWrapper<WindowMainResource>,
    ) : WithKGEState,
        KGEInternalResource {
        private val boundResources = mutableListOf<KGEResource>()

        @KGESensitiveAPI
        override val mainResource: WindowMainResource by mainResourceWrapper

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

        override val fontSheet: Decal
        override var tabSizeInSpaces: Int = KGEConfiguration.defaultTabSizeInSpaces
            set(value) {
                check(value > 0) { "Invalid tab size in spaces: $value" }
                field = value
            }

        init {
            bindResource(mainResourceWrapper)
            fontSheet = letClosingIfFailed { Rasterizer.createFontSheet() }
            bindResource(fontSheet)
        }

        @KGESensitiveAPI
        fun bindResource(resource: KGEResource) = boundResources.add(0, resource)

        @KGESensitiveAPI
        override fun close() = boundResources.invokeForAllRemoving(KGEResource::close)
    }

expect class WindowMainResource
