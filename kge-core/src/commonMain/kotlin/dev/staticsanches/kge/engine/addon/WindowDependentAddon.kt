package dev.staticsanches.kge.engine.addon

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.engine.WindowMainResource
import dev.staticsanches.kge.engine.state.DimensionState
import dev.staticsanches.kge.engine.state.TimeState
import dev.staticsanches.kge.engine.state.WithKGEState
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.LayerDescriptor

interface WindowDependentAddon : WithKGEState {
    val kgeWindow: Window

    @KGESensitiveAPI
    override val mainResource: WindowMainResource
        get() = kgeWindow.mainResource
    override val dimensionState: DimensionState
        get() = kgeWindow.dimensionState
    override val screenSize: Int2D
        get() = kgeWindow.screenSize
    override val invertedScreenSize: Float2D
        get() = kgeWindow.invertedScreenSize
    override val timeState: TimeState
        get() = kgeWindow.timeState
    override var decalMode: Decal.Mode
        get() = kgeWindow.decalMode
        set(value) {
            kgeWindow.decalMode = value
        }
    override var pixelMode: Pixel.Mode
        get() = kgeWindow.pixelMode
        set(value) {
            kgeWindow.pixelMode = value
        }
    override var decalStructure: Decal.Structure
        get() = kgeWindow.decalStructure
        set(value) {
            kgeWindow.decalStructure = value
        }
    override var suspendTextureTransfer: Boolean
        get() = kgeWindow.suspendTextureTransfer
        set(value) {
            kgeWindow.suspendTextureTransfer = value
        }

    @KGESensitiveAPI
    override val layers: ArrayList<LayerDescriptor>
        get() = kgeWindow.layers

    @KGESensitiveAPI
    override var targetLayerIndex: Int
        get() = kgeWindow.targetLayerIndex
        set(value) {
            kgeWindow.targetLayerIndex = value
        }
    override var drawTarget: Sprite?
        get() = kgeWindow.drawTarget
        set(value) {
            kgeWindow.drawTarget = value
        }

    override val fontSheet: Decal
        get() = kgeWindow.fontSheet
    override var tabSizeInSpaces: Int
        get() = kgeWindow.tabSizeInSpaces
        set(value) {
            kgeWindow.tabSizeInSpaces = value
        }
}
