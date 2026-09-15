package dev.staticsanches.kge.engine

import dev.staticsanches.kge.engine.input.KeyboardKey
import dev.staticsanches.kge.engine.input.Modifiers
import dev.staticsanches.kge.engine.input.MouseButton
import dev.staticsanches.kge.engine.input.RawInput
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.device.WebGpuDevice
import dev.staticsanches.kge.renderer.gl.updateGLContext
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlinx.coroutines.suspendCancellableCoroutine
import web.dom.document
import web.events.Event
import web.events.EventTargetLike
import web.events.EventType
import web.events.RESIZE
import web.events.addEventListener
import web.events.removeEventListener
import web.focus.BLUR
import web.focus.FOCUS
import web.focus.FocusEvent
import web.gl.ID
import web.gl.WebGL2RenderingContext
import web.html.HTMLCanvasElement
import web.keyboard.CapsLock
import web.keyboard.KEY_DOWN
import web.keyboard.KEY_UP
import web.keyboard.KeyboardEvent
import web.keyboard.ModifierKeyCode
import web.keyboard.NumLock
import web.mouse.AUXILIARY
import web.mouse.MAIN
import web.mouse.MOUSE_DOWN
import web.mouse.MOUSE_MOVE
import web.mouse.MOUSE_UP
import web.mouse.MouseEvent
import web.mouse.SECONDARY
import web.mouse.WHEEL
import web.mouse.WheelEvent
import kotlin.coroutines.resume
import kotlinx.browser.window as animationWindow
import web.mouse.MouseButton as DomMouseButton
import web.window.window as eventWindow

internal actual val driverServiceDefault: DriverService = DefaultWebDriverService

/**
 * The web [DriverService] bound to a caller-owned [canvas].
 *
 * The driver uses the canvas' WebGL2 context and follows its CSS size on resize.
 * The backing store matches the logical size by default and is scaled by the
 * device pixel ratio when [WindowConfig.highDpi] is set; closing never removes
 * the caller's canvas.
 */
class WebDriverService(
    private val canvas: HTMLCanvasElement,
) : DriverService {
    override fun create(config: WindowConfig): Driver = webDriver(canvas, config, ownsCanvas = false)
}

private object DefaultWebDriverService : DriverService {
    override fun create(config: WindowConfig): Driver {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        document.body.appendChild(canvas)
        return webDriver(canvas, config, ownsCanvas = true)
    }
}

private fun webDriver(
    canvas: HTMLCanvasElement,
    config: WindowConfig,
    ownsCanvas: Boolean,
): WebDriver =
    WebDriver(
        canvas = canvas,
        desiredSize = Int2D(config.screenWidth * config.pixelWidth, config.screenHeight * config.pixelHeight),
        ownsCanvas = ownsCanvas,
        highDpi = config.highDpi,
    ).letClosingIfFailed { driver ->
        driver.initialize()
        driver
    }

private class WebDriver(
    private val canvas: HTMLCanvasElement,
    private val desiredSize: Int2D,
    private val ownsCanvas: Boolean,
    private val highDpi: Boolean,
) : Driver {
    override val input = RawInput()

    private var windowSize = desiredSize
    private var closed = false
    private lateinit var device: WebGpuDevice
    private val disposers = mutableListOf<() -> Unit>()

    fun initialize() {
        device =
            WebGpuDevice(
                checkNotNull(canvas.getContext(WebGL2RenderingContext.ID)) {
                    "WebGL2 context unavailable"
                },
            )
        applySize(desiredSize)
        registerInput()
    }

    private fun applySize(size: Int2D) {
        windowSize = size
        // The backing store is DPR-scaled when the config honors HiDPI;
        // the CSS size stays logical so the element is not displayed DPR-times
        // too large.
        canvas.style.width = "${size.x}px"
        canvas.style.height = "${size.y}px"
        canvas.width = (size.x * pixelRatio).toInt()
        canvas.height = (size.y * pixelRatio).toInt()
    }

    /** The drawable/logical ratio, [drawablePixelRatio] of the display's DPR. */
    private val pixelRatio: Double
        get() = drawablePixelRatio(highDpi, animationWindow.devicePixelRatio)

    private fun registerInput() {
        disposers += eventWindow.on(KeyboardEvent.KEY_DOWN) { onKey(it, down = true) }
        disposers += eventWindow.on(KeyboardEvent.KEY_UP) { onKey(it, down = false) }
        disposers += canvas.on(MouseEvent.MOUSE_DOWN) { onMouseButton(it, down = true) }
        disposers += canvas.on(MouseEvent.MOUSE_UP) { onMouseButton(it, down = false) }
        disposers +=
            canvas.on(MouseEvent.MOUSE_MOVE) {
                input.applyMouseMove(it.offsetX, it.offsetY)
            }
        disposers += canvas.on(WheelEvent.WHEEL) { input.applyScroll(it.deltaY) }
        disposers += eventWindow.on(FocusEvent.BLUR) { onFocus(false) }
        disposers += eventWindow.on(FocusEvent.FOCUS) { onFocus(true) }
        disposers += eventWindow.on(Event.RESIZE) { onResize() }
    }

    private fun onKey(
        event: KeyboardEvent,
        down: Boolean,
    ) {
        input.applyKey(event.code.toString(), event.webModifiers(), down)
    }

    private fun onMouseButton(
        event: MouseEvent,
        down: Boolean,
    ) {
        input.applyMouseButton(event.button, down)
    }

    private fun onFocus(focused: Boolean) {
        input.applyFocus(focused)
    }

    private fun onResize() {
        if (ownsCanvas) {
            applySize(fitCanvasSize(desiredSize, Int2D(eventWindow.innerWidth, eventWindow.innerHeight)))
        } else {
            val width = canvas.clientWidth
            val height = canvas.clientHeight
            if (width > 0 && height > 0) {
                windowSize = Int2D(width, height)
                canvas.width = (width * pixelRatio).toInt()
                canvas.height = (height * pixelRatio).toInt()
            }
        }
    }

    override fun makeCurrent() = device.makeCurrent()

    override fun present() = device.present()

    override fun pollEvents() = Unit

    override suspend fun awaitNextFrame() {
        suspendCancellableCoroutine { continuation ->
            val handle = animationWindow.requestAnimationFrame { continuation.resume(Unit) }
            continuation.invokeOnCancellation { animationWindow.cancelAnimationFrame(handle) }
        }
    }

    override fun windowSize(): Int2D = windowSize

    override fun framebufferSize(): Int2D = Int2D(canvas.width, canvas.height)

    override fun isClosing(): Boolean = closed

    override fun cancelClose() = Unit

    override fun close() {
        if (closed) return
        closed = true
        disposers.forEach { it() }
        disposers.clear()
        updateGLContext(null)
        if (ownsCanvas) canvas.remove()
    }
}

/** Registers [handler] for [type]; the returned action removes it. */
private fun <E : Event> EventTargetLike.on(
    type: EventType<E>,
    handler: (E) -> Unit,
): () -> Unit {
    addEventListener(type, handler)
    return { removeEventListener(type, handler) }
}

/** Applies a keyboard event's code and modifiers to the raw input. */
internal fun RawInput.applyKey(
    code: String,
    modifiers: Modifiers,
    down: Boolean,
) {
    this.modifiers = modifiers
    setKeyDown(KeyboardKey[code], down)
}

/** Applies a DOM mouse button event to the raw input; an unknown button is ignored. */
internal fun RawInput.applyMouseButton(
    button: web.mouse.MouseButton,
    down: Boolean,
) {
    val mapped =
        when (button) {
            DomMouseButton.MAIN -> MouseButton.LEFT
            DomMouseButton.AUXILIARY -> MouseButton.MIDDLE
            DomMouseButton.SECONDARY -> MouseButton.RIGHT
            else -> return
        }
    setMouseButtonDown(mapped, down)
}

/** Applies a DOM cursor position, in window points. */
internal fun RawInput.applyMouseMove(
    x: Double,
    y: Double,
) {
    mousePosition = Int2D(x.toInt(), y.toInt())
}

/**
 * Applies a DOM wheel delta; the DOM reports positive down, the engine (like
 * olc's emscripten backend) positive up.
 */
internal fun RawInput.applyScroll(deltaY: Double) {
    wheelDelta -= deltaY.toInt()
}

/** Applies a focus change; losing focus releases every held key and mouse button. */
internal fun RawInput.applyFocus(focused: Boolean) {
    this.focused = focused
    if (!focused) {
        clearKeys()
        clearMouseButtons()
    }
}

private fun KeyboardEvent.webModifiers(): Modifiers =
    Modifiers.of(
        shift = shiftKey,
        ctrl = ctrlKey,
        alt = altKey,
        superKey = metaKey,
        capsLock = getModifierState(ModifierKeyCode.CapsLock),
        numLock = getModifierState(ModifierKeyCode.NumLock),
    )

/**
 * Fits [desired] into [available] preserving its aspect ratio; a non-positive
 * available axis leaves the desired size unchanged.
 */
internal fun fitCanvasSize(
    desired: Int2D,
    available: Int2D,
): Int2D {
    if (available.x <= 0 || available.y <= 0) return desired
    val scale = minOf(available.x.toDouble() / desired.x, available.y.toDouble() / desired.y)
    return Int2D(
        (desired.x * scale).toInt().coerceAtLeast(1),
        (desired.y * scale).toInt().coerceAtLeast(1),
    )
}

/** The drawable/logical ratio: the display's [devicePixelRatio] when [highDpi], else 1. */
internal fun drawablePixelRatio(
    highDpi: Boolean,
    devicePixelRatio: Double,
): Double = if (highDpi) devicePixelRatio else 1.0
