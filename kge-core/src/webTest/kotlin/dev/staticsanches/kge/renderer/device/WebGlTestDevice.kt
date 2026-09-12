package dev.staticsanches.kge.renderer.device

import dev.staticsanches.kge.renderer.gl.updateGLContext
import web.dom.document
import web.gl.ID
import web.gl.WebGL2RenderingContext
import web.html.HTMLCanvasElement

/**
 * The web real-GL test device: an off-DOM canvas with a WebGL2 context, the
 * browser counterpart of [GlfwTestDevice]. [create] fails fast when Chrome
 * cannot give a WebGL2 context.
 *
 * [close] clears the process-wide context install the device made current, so
 * a later test never observes a stale context. The canvas stays referenced
 * through the context for the device's lifetime.
 */
internal class WebGlTestDevice private constructor(
    private val canvas: HTMLCanvasElement,
    private val device: WebGpuDevice,
) : GpuDevice by device,
    AutoCloseable {
    override fun close() = updateGLContext(null)

    companion object {
        fun create(
            width: Int,
            height: Int,
        ): WebGlTestDevice {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.width = width
            canvas.height = height
            val context =
                checkNotNull(canvas.getContext(WebGL2RenderingContext.ID)) {
                    "WebGL2 context unavailable"
                }
            return WebGlTestDevice(canvas, WebGpuDevice(context))
        }
    }
}
