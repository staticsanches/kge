package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.input.InputState
import dev.staticsanches.kge.engine.input.InputTracker
import dev.staticsanches.kge.engine.layer.LayerStack
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.text.DrawStringService
import dev.staticsanches.kge.time.FrameAccumulator
import dev.staticsanches.kge.time.Time
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

/**
 * The engine loop: opens the platform window, owns the built-in GPU resources
 * and drives the user callbacks.
 *
 * The loop is confined to the thread that calls [start]. Subclasses script the
 * application through [onUserCreate], [onUserUpdate] and [onUserDestroy], each
 * returning `false` to abort, leave or restart the loop as documented.
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class Engine(
    protected val config: WindowConfig,
) : HasWindow,
    HasTime,
    HasInput,
    HasLayers,
    HasDrawTarget,
    HasDrawModes,
    HasDriver,
    HasResourceScope {
    private val active = AtomicBoolean(false)
    private val accumulator = FrameAccumulator()
    private val inputTracker = InputTracker()

    override val window: WindowInfo = WindowInfo(config)

    private var engineLayers: LayerStack? = null

    /**
     * The engine's layers; fails fast when read before [start] or after it
     * returns. Layer 0 exists for the whole run.
     */
    override val layers: LayerStack
        get() = engineLayers ?: error("layers is only available while the engine is running")

    /**
     * The sprite the engine draws into; it points at layer 0's target at
     * startup. A non-null assignment keeps the selected layer, `null` selects
     * layer 0.
     */
    override var drawTarget: Sprite? = null
        set(value) {
            requireEngineThread()
            if (value == null) {
                layers.targetIndex = 0
                field = layers[0].target
            } else {
                field = value
            }
        }

    /** How raster primitives blend into [drawTarget]. */
    override var pixelMode: Pixel.Mode = Pixel.Mode.Normal

    /** How decals blend into [drawTarget]. */
    override var decalMode: Decal.Mode = Decal.Mode.NORMAL

    /** How a decal instance's vertex list is assembled into primitives. */
    override var decalStructure: Decal.Structure = Decal.Structure.FAN

    /** Suppresses a layer's automatic CPU → GPU upload before it is composited. */
    override var suspendTextureTransfer: Boolean = false

    override fun setDrawTarget(
        index: Int,
        dirty: Boolean,
    ) {
        requireEngineThread()
        val layer = layers[index]
        layers.targetIndex = index
        layer.update = dirty
        drawTarget = layer.target
    }

    private var engineDispatcher: CoroutineDispatcher? = null
    private var engineThreadId: Long? = null
    private var engineDriver: Driver? = null
    private var engineScope: ResourceScope? = null

    /**
     * The driver of the current run; fails fast when read before [start] or
     * after it returns.
     */
    @KGESensitiveAPI
    override val driver: Driver
        get() = engineDriver ?: error("driver is only available while the engine is running")

    /**
     * The scope that owns the run's resources; fails fast when read before
     * [start] or after it returns.
     */
    @KGESensitiveAPI
    override val resourceScope: ResourceScope
        get() = engineScope ?: error("resourceScope is only available while the engine is running")

    private var lastFramebufferSize: Int2D? = null
    private var viewportFit: ViewportFit? = null

    /** The snapshot of the last rendered frame; zero before [start]. */
    final override var frame: FrameInfo = FrameInfo(Duration.ZERO, 0, 0, Int2D(0, 0))
        private set

    /** The input snapshot of the current frame; it refreshes before each [onUserUpdate]. */
    override val input: InputState
        get() = inputTracker.state

    /**
     * The dispatcher confined to the thread that called [start]; every callback
     * runs on it. Fails fast when read before [start].
     */
    val dispatcher: CoroutineDispatcher
        get() = engineDispatcher ?: error("dispatcher is only available while the engine is running")

    /** Called once, after the window and the built-in resources are ready. */
    open suspend fun onUserCreate(): Boolean = true

    /** Called once per frame with the time elapsed since the previous frame. */
    open suspend fun onUserUpdate(elapsed: Duration): Boolean = true

    /**
     * Called when the loop stops; `false` vetoes a platform close request
     * (clearing it) and restarts the loop without re-creating the window.
     */
    open suspend fun onUserDestroy(): Boolean = true

    /** Opens the window and runs the loop until a callback or the platform stops it. */
    suspend fun start() {
        engineDispatcher =
            coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
                ?: error("start() must run on a coroutine dispatcher")
        engineThreadId = currentThreadId()
        lastFramebufferSize = null
        viewportFit = null

        try {
            // The driver is closed on every exit path; the scope first, so GPU
            // resources are released while the context is still alive.
            DriverService.create(config).use { driver ->
                engineDriver = driver
                driver.makeCurrent()
                ResourceScope().use { scope ->
                    engineScope = scope
                    Renderer.createResources(driver, scope)
                    DrawStringService.createResources(scope)
                    val layerStack = LayerStack(window.screenSize.x, window.screenSize.y)
                    engineLayers = layerStack
                    scope.register(LayersKey, layerStack)
                    drawTarget = null
                    runLoop(driver, scope)
                }
            }
        } finally {
            engineLayers = null
            engineDriver = null
            engineScope = null
            engineThreadId = null
            KGEOverridable.Proxy.resetAll()
        }
    }

    /**
     * Requests the loop to stop; safe to call from any thread. The destroy
     * callback is still consulted, and returning `false` restarts the loop.
     */
    fun stop() {
        active.store(false)
    }

    /**
     * Fails fast unless called on the thread that called [start]; the check is
     * unavailable before [start] and after it returns.
     */
    fun requireEngineThread() {
        val expected =
            engineThreadId
                ?: error("requireEngineThread is only available while the engine is running")
        check(currentThreadId() == expected) {
            "Requires the engine thread, was thread ${currentThreadId()} instead of $expected"
        }
    }

    /**
     * Changes the game screen to [width] x [height], re-allocating every layer
     * at the new size and selecting layer 0 as the draw target. The viewport
     * re-fits on the next frame. Both dimensions must be positive and the call
     * must run on the engine thread.
     */
    fun setScreenSize(
        width: Int,
        height: Int,
    ) {
        require(width > 0 && height > 0) { "setScreenSize requires a positive size, was ${width}x$height" }
        requireEngineThread()
        layers.resizeAll(width, height)
        window.screenSize = Int2D(width, height)
        drawTarget = null
        viewportFit = null
    }

    private suspend fun runLoop(
        driver: Driver,
        scope: ResourceScope,
    ) {
        active.store(onUserCreate())
        while (active.load()) {
            while (active.load() && !driver.isClosing()) {
                val elapsed = accumulator.tick(Time.elapsed())
                driver.pollEvents()
                val framebufferSize = driver.framebufferSize()
                window.windowSize = driver.windowSize()
                window.framebufferSize = framebufferSize
                if (viewportFit == null || framebufferSize != lastFramebufferSize) {
                    lastFramebufferSize = framebufferSize
                    viewportFit = fitViewport(window.screenSize, window.pixelSize, framebufferSize, config.cohesion)
                }
                val fit = checkNotNull(viewportFit)
                inputTracker.latch(driver.input, window.screenSize, fit, window.windowSize, framebufferSize)
                if (!onUserUpdate(elapsed)) active.store(false)
                renderFrame(scope, driver, fit)
                frame = FrameInfo(elapsed, accumulator.fps, accumulator.frameCount, framebufferSize)
            }
            val closing = driver.isClosing()
            val destroyed = onUserDestroy()
            if (destroyed) break
            if (closing) driver.cancelClose()
            active.store(true)
        }
    }

    private suspend fun renderFrame(
        scope: ResourceScope,
        driver: Driver,
        fit: ViewportFit,
    ) {
        Renderer.updateViewport(fit.position, fit.size)
        Renderer.clearBuffer(config.clearColor, depth = true)

        // Layer 0 is always composited and uploaded; the decal mode resets every frame.
        layers[0].show = true
        layers[0].update = true
        decalMode = Decal.Mode.NORMAL

        Renderer.prepareDrawing(scope)

        for (index in layers.size - 1 downTo 0) {
            val layer = layers[index]
            if (!layer.show) continue
            val customRender = layer.customRender
            if (customRender != null) {
                customRender(layer)
            } else {
                Renderer.applyTexture(layer.decal.texture)
                if (!suspendTextureTransfer && layer.update) {
                    layer.decal.update()
                    layer.update = false
                }
                Renderer.drawLayerQuad(scope, layer.offset, layer.scale, layer.tint)
                layer.decalInstances.forEach { Renderer.drawDecal(scope, it) }
                layer.decalInstances.clear()
            }
        }

        driver.present()
        driver.awaitNextFrame()
    }
}

/** The OS identity of the calling thread — the engine's thread identity. */
internal expect fun currentThreadId(): Long

/** The engine's layer stack, registered so it closes before the renderer's built-in resources. */
private object LayersKey : ResourceScope.Key<LayerStack>
