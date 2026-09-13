package dev.staticsanches.kge.engine

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.device.WebGpuDevice
import dev.staticsanches.kge.renderer.gl.updateGLContext
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import web.dom.document
import web.gl.ID
import web.gl.WebGL2RenderingContext
import web.html.HTMLCanvasElement
import kotlin.coroutines.resume

internal actual val driverServiceDefault: DriverService = DefaultWebDriverService

/**
 * The web [DriverService] bound to a caller-owned [canvas].
 *
 * The driver uses the canvas' WebGL2 context and resizes the canvas backing
 * store to the physical device pixels; closing it never removes the caller's
 * canvas.
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
    WebDriver(canvas, config, ownsCanvas).letClosingIfFailed { driver ->
        driver.initialize()
        driver
    }

private class WebDriver(
    private val canvas: HTMLCanvasElement,
    config: WindowConfig,
    private val ownsCanvas: Boolean,
) : Driver {
    private val windowSize =
        Int2D(config.screenWidth * config.pixelWidth, config.screenHeight * config.pixelHeight)
    private lateinit var device: WebGpuDevice
    private var closed = false

    fun initialize() {
        device =
            WebGpuDevice(
                checkNotNull(canvas.getContext(WebGL2RenderingContext.ID)) {
                    "WebGL2 context unavailable"
                },
            )
        canvas.width = (windowSize.x * window.devicePixelRatio).toInt()
        canvas.height = (windowSize.y * window.devicePixelRatio).toInt()
        // The backing store is DPR-scaled; the CSS size stays logical so the
        // element is not displayed DPR-times too large.
        canvas.style.width = "${windowSize.x}px"
        canvas.style.height = "${windowSize.y}px"
    }

    override fun makeCurrent() = device.makeCurrent()

    override fun present() = device.present()

    override fun pollEvents() = Unit

    override suspend fun awaitNextFrame() {
        suspendCancellableCoroutine { continuation ->
            val handle = window.requestAnimationFrame { continuation.resume(Unit) }
            continuation.invokeOnCancellation { window.cancelAnimationFrame(handle) }
        }
    }

    override fun windowSize(): Int2D = windowSize

    override fun framebufferSize(): Int2D = Int2D(canvas.width, canvas.height)

    override fun isClosing(): Boolean = closed

    override fun cancelClose() = Unit

    override fun close() {
        if (closed) return
        closed = true
        updateGLContext(null)
        if (ownsCanvas) canvas.remove()
    }
}
