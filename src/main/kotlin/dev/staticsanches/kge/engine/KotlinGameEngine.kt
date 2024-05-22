package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGEAllOpen
import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.addon.CallbacksAddon
import dev.staticsanches.kge.engine.addon.ClearAddon
import dev.staticsanches.kge.engine.addon.DrawAddon
import dev.staticsanches.kge.engine.addon.DrawLineAddon
import dev.staticsanches.kge.engine.addon.DrawRectAddon
import dev.staticsanches.kge.engine.addon.DrawTriangleAddon
import dev.staticsanches.kge.engine.addon.ExtensionsAddon
import dev.staticsanches.kge.engine.addon.FillRectAddon
import dev.staticsanches.kge.engine.addon.LayersAddon
import dev.staticsanches.kge.engine.addon.ScreenSizeAddon
import dev.staticsanches.kge.engine.addon.WindowManipulationAddon
import dev.staticsanches.kge.engine.extension.KGEX
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.input.KeyboardKey
import dev.staticsanches.kge.input.KeyboardModifiers
import dev.staticsanches.kge.input.PressAction
import dev.staticsanches.kge.input.ReleaseAction
import dev.staticsanches.kge.input.RepeatAction
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.types.vector.by
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Platform

@KGEAllOpen
class KotlinGameEngine(val appName: String) :
    CallbacksAddon,
    ClearAddon,
    DrawAddon,
    DrawLineAddon,
    DrawRectAddon,
    DrawTriangleAddon,
    ExtensionsAddon,
    FillRectAddon,
    LayersAddon,
    ScreenSizeAddon,
    WindowManipulationAddon {
    fun start(initializer: Configurator.() -> Unit): Unit =
        try {
            Configurator().apply(initializer).createWindow().use { it.doStart() }
        } finally {
            GLFW.glfwTerminate()
            GLFW.glfwSetErrorCallback(null)?.free()
        }

    private fun Window.doStart() {
        // Create primary layer 0
        createLayer()
        layers[0].apply {
            update = true
            show = true
        }
        drawTarget = null

        extensions.forEach { it.onBeforeUserCreate() }
        onUserCreate()
        extensions.forEach { it.onAfterUserCreate() }

        while (!windowShouldClose) {
            showWindow()

            timeState.reset()
            while (!windowShouldClose) {
                coreUpdate()
                pollEvents()
            }

            extensions.forEach { it.onBeforeUserDestroy() }
            if (!onUserDestroy()) windowShouldClose = false
            extensions.forEach { it.onAfterUserDestroy() }
        }
    }

    @KGESensitiveAPI
    private fun Window.coreUpdate() {
        timeState.tick()

        val blockedFrame = extensions.fold(false) { blocked, extension -> extension.onBeforeUserUpdate() || blocked }
        if (!blockedFrame) {
            if (!onUserUpdate()) {
                windowShouldClose = true
            }
        }
        extensions.forEach { it.onAfterUserUpdate() }

        // Display frame
        Renderer.updateViewport(dimensionState.viewportPosition, dimensionState.viewportSize)
        Renderer.clearBuffer(Colors.BLACK, true)

        // Layer 0 must always exist
        layers[0].update = true
        layers[0].show = true

        decalMode = Decal.Mode.NORMAL

        Renderer.prepareDrawing()

        layers.reversed().asSequence()
            .filter { it.show }
            .forEach { layer ->
                val functionHook = layer.functionHook
                if (functionHook == null) {
                    Renderer.applyTexture(layer.drawTarget.id)
                    if (!suspendTextureTransfer && layer.update) {
                        layer.drawTarget.update()
                        layer.update = false
                    }
                    Renderer.drawLayerQuad(layer.offset, layer.scale, layer.tint)

                    // Display Decals in order for this layer
                    layer.decalInstances.forEach { Renderer.drawDecal(it) }

                    layer.decalInstances.clear()
                } else {
                    functionHook(layer)
                }
            }

        Renderer.displayFrame()

        // Updates the window title
        if (timeState.fpsChanged) {
            changeWindowTitle("staticsanches.dev | kotlin Game Engine | $appName | FPS: ${timeState.fps}")
        }
    }

    @KGESensitiveAPI
    fun pollEvents() {
        GLFW.glfwPollEvents()
    }

    private fun Configurator.createWindow(): Window {
        if (Platform.get() == Platform.MACOSX) Configuration.GLFW_LIBRARY_NAME.set("glfw_async")

        GLFWErrorCallback.create(::onGLFWError).set()
        check(GLFW.glfwInit()) { "Unable to initialize GLFW" }

        check(screenWidth > 0) { "Screen width must be greater than 0" }
        check(screenHeight > 0) { "Screen height must be greater than 0" }
        check(pixelWidth > 0) { "Pixel width must be greater than 0" }
        check(pixelHeight > 0) { "Pixel height must be greater than 0" }

        // Create the window
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, if (resizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER, if (enableRetina) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
        Renderer.beforeWindowCreation()

        val windowWidth = screenWidth * pixelWidth
        val windowHeight = screenHeight * pixelHeight
        val windowHandle =
            GLFW.glfwCreateWindow(
                windowWidth,
                windowHeight,
                appName,
                if (fullScreen) GLFW.glfwGetPrimaryMonitor() else MemoryUtil.NULL,
                MemoryUtil.NULL,
            )
        check(windowHandle != MemoryUtil.NULL) { "Failed to create the GLFW window" }

        if (keepAspectRatio) {
            GLFW.glfwSetWindowAspectRatio(windowHandle, windowWidth, windowHeight)
        }

        GLFW.glfwMakeContextCurrent(windowHandle)
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_LOCK_KEY_MODS, GLFW.GLFW_TRUE)

        GL.createCapabilities()
        val window = Window(windowHandle)

        // Position the window
        if (!fullScreen) {
            if (centerWindow) {
                val mode =
                    checkNotNull(GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor())) {
                        "Unable to retrieve monitor video mode"
                    }
                GLFW.glfwSetWindowPos(
                    windowHandle,
                    (mode.width() - windowWidth) / 2,
                    (mode.height() - windowHeight) / 2,
                )
            } else {
                GLFW.glfwSetWindowPos(windowHandle, windowPositionX, windowPositionY)
            }
        }

        GLFW.glfwSwapInterval(if (vSync) 1 else 0)

        val dimensionState = window.dimensionState

        dimensionState.screenSize = screenWidth by screenHeight
        dimensionState.pixelSize = pixelWidth by pixelHeight

        // Update the real window size
        val width = IntArray(1)
        val height = IntArray(1)

        GLFW.glfwGetFramebufferSize(windowHandle, width, height)
        dimensionState.windowSizeInPixels = width[0] by height[0]

        GLFW.glfwGetWindowSize(windowHandle, width, height)
        dimensionState.windowSize = width[0] by height[0]

        dimensionState.windowPixelSize = dimensionState.windowSizeInPixels / dimensionState.windowSize

        dimensionState.updateViewport()

        window.registerCallbacks()

        with(window) {
            Renderer.afterWindowCreation()
            Renderer.updateViewport(dimensionState.viewportPosition, dimensionState.viewportSize)
            Renderer.clearBuffer(Colors.BLACK, true)
        }

        return window
    }

    private fun Window.registerCallbacks() {
        val windowHandle = glfwHandle

        val widthArray = IntArray(1)
        val heightArray = IntArray(1)
        GLFW.glfwSetFramebufferSizeCallback(windowHandle) { window, width, height ->
            check(window == windowHandle) { "Invalid window handle" }

            dimensionState.windowSizeInPixels = width by height

            GLFW.glfwGetWindowSize(windowHandle, widthArray, heightArray)
            dimensionState.windowSize = widthArray[0] by heightArray[0]

            dimensionState.windowPixelSize = dimensionState.windowSizeInPixels / dimensionState.windowSize

            dimensionState.updateViewport()
            onFrameBufferResize(width, height)
        }

        GLFW.glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
            check(window == windowHandle) { "Invalid window handle" }

            val keyboardKey = KeyboardKey[key]
            val keyboardKeyAction =
                when (action) {
                    GLFW.GLFW_PRESS -> PressAction
                    GLFW.GLFW_RELEASE -> ReleaseAction
                    GLFW.GLFW_REPEAT -> RepeatAction
                    else -> throw RuntimeException("Invalid keyboard action: $action")
                }

            val newModifiers = KeyboardModifiers(mods)

            onKeyEvent(keyboardKey, keyboardKeyAction, scancode, newModifiers)

            // Update the state
            inputState.keyboardModifiers = newModifiers
            inputState.keyboardKeyState[keyboardKey] = keyboardKeyAction
        }
    }

    private final val extensions = ArrayList<KGEX>()

    override fun registerExtension(extensionProvider: (KotlinGameEngine) -> KGEX) {
        extensions.add(extensionProvider(this))
    }

    class Configurator {
        var screenWidth: Int = 150
        var screenHeight: Int = 100
        var pixelWidth: Int = 4
        var pixelHeight: Int = 4

        var centerWindow: Boolean = false
        var windowPositionX: Int = 50
        var windowPositionY: Int = 50

        var resizable: Boolean = false
        var keepAspectRatio: Boolean = false
        var fullScreen: Boolean = false

        var vSync: Boolean = false
        var enableRetina: Boolean = false
    }
}
