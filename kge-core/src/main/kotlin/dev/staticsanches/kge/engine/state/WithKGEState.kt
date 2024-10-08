package dev.staticsanches.kge.engine.state

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.LayerDescriptor

interface WithKGEState {
    @KGESensitiveAPI
    val glfwHandle: Long

    val dimensionState: DimensionState
    val screenSize: Int2D
    val invertedScreenSize: Float2D

    val inputState: InputState
    val timeState: TimeState

    var decalMode: Decal.Mode
    var pixelMode: Pixel.Mode
    var decalStructure: Decal.Structure
    var suspendTextureTransfer: Boolean

    @KGESensitiveAPI
    val layers: ArrayList<LayerDescriptor>

    @KGESensitiveAPI
    var targetLayerIndex: Int

    val targetLayer: LayerDescriptor
        get() = layers[targetLayerIndex]

    var drawTarget: Sprite?
        @KGESensitiveAPI set
}
