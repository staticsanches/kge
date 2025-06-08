package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.state.input.KeyboardKey
import dev.staticsanches.kge.engine.state.input.KeyboardModifiers
import dev.staticsanches.kge.engine.state.input.PressAction
import dev.staticsanches.kge.engine.state.input.ReleaseAction
import dev.staticsanches.kge.engine.state.input.RepeatAction
import dev.staticsanches.kge.engine.state.input.UnknownAction
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.gl.updateGLContext
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.ResourceWrapper.Companion.invoke
import dev.staticsanches.kge.resource.applyClosingIfFailed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import web.animations.awaitAnimationFrame
import web.dom.document
import web.events.addEventListener
import web.events.removeEventListener
import web.html.HTMLElement
import web.uievents.KeyboardEvent

abstract class KotlinGameEngine : KotlinGameEngineBase {
    private var internalWindow: Window? = null
    final override val kgeWindow: Window
        get() =
            internalWindow
                ?: throw IllegalStateException("Window and its info are only available while the engine is running")

    @KGESensitiveAPI
    var shouldStop: Boolean = false

    fun run(
        canvasHolder: HTMLElement,
        init: Configurator.() -> Unit,
    ) {
        MainScope().launch {
            Configurator(canvasHolder).apply(init).createWindow().use { window ->
                try {
                    internalWindow = window

                    window.registerCallbacks(this@launch)

                    start()
                } finally {
                    internalWindow = null
                    this@launch.cancel()
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

    private fun Window.registerCallbacks(scope: CoroutineScope) {
        bindResource(KeyboardEventHandlerResource(scope))
    }

    class Configurator internal constructor(
        val canvasHolder: HTMLElement,
    ) {
        var screenWidth: Int = 150
        var screenHeight: Int = 100
        var pixelWidth: Int = 4
        var pixelHeight: Int = 4
    }

    private inner class KeyboardEventHandlerResource private constructor(
        val handler: (KeyboardEvent) -> Unit,
    ) : KGEResource {
        constructor(scope: CoroutineScope) : this({ e ->
            scope.launch {
                val keyboardKey = KeyboardKey[e]
                val keyboardKeyAction =
                    when (e.type) {
                        KeyboardEvent.KEY_UP -> ReleaseAction
                        KeyboardEvent.KEY_DOWN ->
                            when (inputState.keyboardKeyState[keyboardKey]) {
                                PressAction -> RepeatAction
                                ReleaseAction -> PressAction
                                RepeatAction -> RepeatAction
                                UnknownAction -> PressAction
                            }
                    }
                val newModifiers = KeyboardModifiers(e)

                onKeyboardEvent(keyboardKey, keyboardKeyAction, newModifiers, e)

                // Update the state
                inputState.keyboardModifiers = newModifiers
                inputState.keyboardKeyState[keyboardKey] = keyboardKeyAction
            }
        })

        override fun close() {
            document.removeEventListener(KeyboardEvent.KEY_DOWN, handler)
            document.removeEventListener(KeyboardEvent.KEY_UP, handler)
        }

        init {
            document.addEventListener(KeyboardEvent.KEY_DOWN, handler)
            document.addEventListener(KeyboardEvent.KEY_UP, handler)
        }
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
