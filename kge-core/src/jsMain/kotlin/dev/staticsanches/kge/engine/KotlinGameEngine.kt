package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.addon.CallbacksAddon
import dev.staticsanches.kge.engine.addon.ClearAddon
import dev.staticsanches.kge.engine.addon.DrawAddon
import dev.staticsanches.kge.engine.addon.DrawCircleAddon
import dev.staticsanches.kge.engine.addon.DrawDecalAddon
import dev.staticsanches.kge.engine.addon.DrawLineAddon
import dev.staticsanches.kge.engine.addon.DrawPartialDecalAddon
import dev.staticsanches.kge.engine.addon.DrawRectAddon
import dev.staticsanches.kge.engine.addon.DrawSpriteAddon
import dev.staticsanches.kge.engine.addon.DrawStringAddon
import dev.staticsanches.kge.engine.addon.DrawTriangleAddon
import dev.staticsanches.kge.engine.addon.FillCircleAddon
import dev.staticsanches.kge.engine.addon.FillRectAddon
import dev.staticsanches.kge.engine.addon.FillTriangleAddon
import dev.staticsanches.kge.engine.addon.LayersAddon
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.gl.updateGLContext
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.ResourceWrapper.Companion.invoke
import dev.staticsanches.kge.resource.applyClosingIfFailed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import web.animations.awaitAnimationFrame
import web.html.HTMLElement

actual abstract class KotlinGameEngine :
    CallbacksAddon,
    ClearAddon,
    DrawAddon,
    DrawCircleAddon,
    DrawDecalAddon,
    DrawLineAddon,
    DrawPartialDecalAddon,
    DrawRectAddon,
    DrawSpriteAddon,
    DrawStringAddon,
    DrawTriangleAddon,
    FillCircleAddon,
    FillRectAddon,
    FillTriangleAddon,
    LayersAddon {
    private var internalWindow: Window? = null
    actual final override val kgeWindow: Window
        get() =
            internalWindow
                ?: throw IllegalStateException("Window and its info are only available while the engine is running")

    private var internalScope: CoroutineScope? = null

    @KGESensitiveAPI
    var shouldStop: Boolean = false

    fun run(
        canvasHolder: HTMLElement,
        init: Configurator.() -> Unit,
    ) {
        MainScope().launch {
            Configurator(canvasHolder).apply(init).createWindow().use { window ->
                try {
                    internalScope = this@launch
                    internalWindow = window
                    start()
                } finally {
                    internalScope = null
                    internalWindow = null
                }
            }
        }
    }

    private suspend fun start() {
        Renderer.updateViewport(dimensionState.viewportPosition, dimensionState.viewportSize)
        Renderer.clearBuffer(depth = true)

        // Create primary layer 0
        createLayer()
        layers[0].apply {
            update = true
            show = true
        }
        drawTarget = null

        onUserCreate()

        while (!shouldStop) {
            timeState.reset()

            while (!shouldStop) {
                coreUpdate()
                awaitAnimationFrame()
            }

            if (!onUserDestroy()) shouldStop = false
        }
    }

    private suspend fun coreUpdate() {
        timeState.tick()

        shouldStop = !onUserUpdate()

        // Display frame
        val newWindowSize = mainResource.resizeCanvas()
        if (newWindowSize != dimensionState.windowSize) {
            dimensionState.windowSize = newWindowSize
            dimensionState.windowSizeInPixels = newWindowSize
            dimensionState.windowPixelSize = Int2D.oneByOne
            dimensionState.recalculateViewport()
            Renderer.updateViewport(dimensionState.viewportPosition, dimensionState.viewportSize)
        }

        Renderer.clearBuffer(depth = true)

        // Layer 0 must always exist
        layers[0].update = true
        layers[0].show = true

        decalMode = Decal.Mode.NORMAL

        Renderer.prepareDrawing()

        for (i in layers.size - 1 downTo 0) {
            val layer = layers[i]
            if (!layer.show) continue

            val functionHook = layer.functionHook
            if (functionHook == null) {
                Renderer.applyTexture(layer.drawTarget.resource)
                if (!suspendTextureTransfer && layer.update) {
                    layer.drawTarget.update()
                    layer.update = false
                }
                Renderer.drawLayerQuad(layer.offset, layer.scale, layer.tint)

                // Display Decals in order for this layer
                Renderer.drawDecals(layer.decalInstances)
                layer.decalInstances.clear()
            } else {
                functionHook(layer)
            }
        }

        Renderer.displayFrame()
    }

    class Configurator internal constructor(
        val canvasHolder: HTMLElement,
    ) {
        var screenWidth: Int = 150
        var screenHeight: Int = 100
        var pixelWidth: Int = 4
        var pixelHeight: Int = 4
    }

    companion object {
        private fun Configurator.createWindow(): Window {
            check(pixelWidth > 0) { "Pixel width must be greater than 0" }
            check(pixelHeight > 0) { "Pixel height must be greater than 0" }

            Renderer.beforeWindowCreation()

            return Window(
                ResourceWrapper(
                    "KGE Window",
                    WindowMainResource(canvasHolder),
                    clearGLContext,
                ),
            ).applyClosingIfFailed {
                dimensionState.screenSize = screenWidth by screenHeight
                dimensionState.pixelSize = pixelWidth by pixelHeight
                dimensionState.windowPixelSize = Int2D.oneByOne
                dimensionState.windowSize = mainResource.resizeCanvas()
                dimensionState.windowSizeInPixels = dimensionState.windowSize
                dimensionState.recalculateViewport()

                Renderer.afterWindowCreation(this)
            }
        }

        private val clearGLContext = KGECleanAction { updateGLContext(null) }
    }
}
