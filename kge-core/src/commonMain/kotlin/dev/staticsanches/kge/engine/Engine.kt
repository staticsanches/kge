package dev.staticsanches.kge.engine

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.resource.ResourceScope
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
) {
    private val active = AtomicBoolean(false)
    private val accumulator = FrameAccumulator()

    private var engineDispatcher: CoroutineDispatcher? = null
    private var engineThreadId: Long? = null

    private var lastFramebufferSize: Int2D? = null
    private var viewportFit: ViewportFit? = null

    /** The snapshot of the last rendered frame; zero before [start]. */
    var frame: FrameInfo = FrameInfo(Duration.ZERO, 0, 0)
        private set

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
                driver.makeCurrent()
                ResourceScope().use { scope ->
                    Renderer.createResources(driver, scope)
                    runLoop(driver, scope)
                }
            }
        } finally {
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
     * unavailable before [start].
     */
    fun requireEngineThread() {
        val expected =
            engineThreadId
                ?: error("requireEngineThread is only available while the engine is running")
        check(currentThreadId() == expected) {
            "Requires the engine thread, was thread ${currentThreadId()} instead of $expected"
        }
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
                if (!onUserUpdate(elapsed)) active.store(false)
                renderFrame(scope, driver)
                frame = FrameInfo(elapsed, accumulator.fps, accumulator.frameCount)
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
    ) {
        val framebufferSize = driver.framebufferSize()
        if (framebufferSize != lastFramebufferSize) {
            lastFramebufferSize = framebufferSize
            viewportFit =
                fitViewport(
                    screenSize = Int2D(config.screenWidth, config.screenHeight),
                    pixelSize = Int2D(config.pixelWidth, config.pixelHeight),
                    framebufferSize = framebufferSize,
                    cohesion = config.cohesion,
                )
        }
        val fit = checkNotNull(viewportFit)
        Renderer.updateViewport(fit.position, fit.size)
        Renderer.clearBuffer(Colors.BLACK, depth = true)
        Renderer.prepareDrawing(scope)
        driver.present()
        driver.awaitNextFrame()
    }
}

/** The OS identity of the calling thread — the engine's thread identity. */
internal expect fun currentThreadId(): Long
