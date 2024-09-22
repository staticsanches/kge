package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.engine.state.DimensionState
import dev.staticsanches.kge.engine.state.InputState
import dev.staticsanches.kge.engine.state.TimeState
import dev.staticsanches.kge.engine.state.WithKGEState
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.LayerDescriptor

interface WindowDependentAddon : WithKGEState {
    val window: Window

    @KGESensitiveAPI
    override val glfwHandle: Long
        get() = window.glfwHandle
    override val dimensionState: DimensionState
        get() = window.dimensionState
    override val screenSize: Int2D
        get() = window.screenSize
    override val invertedScreenSize: Float2D
        get() = window.invertedScreenSize
    override val inputState: InputState
        get() = window.inputState
    override val timeState: TimeState
        get() = window.timeState
    override var decalMode: Decal.Mode
        get() = window.decalMode
        set(value) {
            window.decalMode = value
        }
    override var pixelMode: Pixel.Mode
        get() = window.pixelMode
        set(value) {
            window.pixelMode = value
        }
    override var decalStructure: Decal.Structure
        get() = window.decalStructure
        set(value) {
            window.decalStructure = value
        }
    override var suspendTextureTransfer: Boolean
        get() = window.suspendTextureTransfer
        set(value) {
            window.suspendTextureTransfer = value
        }

    @KGESensitiveAPI
    override val layers: ArrayList<LayerDescriptor>
        get() = window.layers

    @KGESensitiveAPI
    override var targetLayerIndex: Int
        get() = window.targetLayerIndex
        set(value) {
            window.targetLayerIndex = value
        }
    override var drawTarget: Sprite?
        get() = window.drawTarget
        set(value) {
            window.drawTarget = value
        }
}
