package dev.staticsanches.kge.renderer.device

import dev.staticsanches.kge.renderer.gl.updateGLContext
import web.gl.WebGL2RenderingContext

/**
 * Web device over a [WebGL2RenderingContext] the owner created. [makeCurrent]
 * installs it process-wide for the backend; [present] is a no-op because the
 * browser composites the canvas at the end of the task. The owner clears the
 * context when the device is retired.
 */
internal class WebGpuDevice(
    private val context: WebGL2RenderingContext,
) : GpuDevice {
    override fun makeCurrent() = updateGLContext(context)

    override fun present() = Unit
}
