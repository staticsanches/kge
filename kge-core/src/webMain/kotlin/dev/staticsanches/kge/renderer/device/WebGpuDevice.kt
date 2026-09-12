package dev.staticsanches.kge.renderer.device

import dev.staticsanches.kge.renderer.gl.updateGLContext
import web.gl.WebGL2RenderingContext

/**
 * Web device over a [WebGL2RenderingContext] the owner created (the engine's
 * window concept and the canvas test harness). There is no context switch to
 * make on the browser's single thread: [makeCurrent] installs [context] as the
 * context the WebGL2 backend operates on, and [present] is a no-op because the
 * browser composites the canvas at the end of the task.
 *
 * The context install is process-wide ([updateGLContext]); the owner is
 * responsible for clearing it when the device is no longer used.
 */
internal class WebGpuDevice(
    private val context: WebGL2RenderingContext,
) : GpuDevice {
    override fun makeCurrent() = updateGLContext(context)

    override fun present() = Unit
}
