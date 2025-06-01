@file:Suppress("unused")

package dev.staticsanches.kge.engine

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.Int2D.Companion.by
import dev.staticsanches.kge.renderer.gl.updateGLContext
import web.dom.document
import web.gl.WebGL2RenderingContext
import web.html.HTMLCanvasElement
import web.html.HTMLElement

actual class WindowMainResource(
    canvasHolder: HTMLElement,
) {
    @KGESensitiveAPI
    val webGL2Context: WebGL2RenderingContext

    @KGESensitiveAPI
    val webGL2Canvas: HTMLCanvasElement
        get() = webGL2Context.canvas as HTMLCanvasElement

    init {
        canvasHolder.style.position = "relative"

        val canvas = document.createElement("canvas", js("{ premultipliedAlpha: false }")) as HTMLCanvasElement
        canvas.style.width = "100%"
        canvas.style.height = "100%"
        canvas.style.position = "absolute"
        canvas.style.left = "0px"
        canvas.style.top = "0px"
        canvasHolder.append(canvas)

        webGL2Context = canvas.getContext(WebGL2RenderingContext.ID)
            ?: throw IllegalStateException("Unable to retrieve GL context")

        updateGLContext(webGL2Context)
    }

    @KGESensitiveAPI
    fun resizeCanvas(): Int2D =
        with(webGL2Canvas) {
            val newWidth = clientWidth
            val newHeight = clientHeight
            if (newWidth != width || newHeight != height) {
                width = newWidth
                height = newHeight
            }
            return newWidth by newHeight
        }
}
