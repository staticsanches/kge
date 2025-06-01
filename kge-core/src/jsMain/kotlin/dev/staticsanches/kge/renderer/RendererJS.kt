package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel

actual val originalRendererImplementation: Renderer
    get() = DefaultRenderer

actual val originalDefaultClearBufferColor: Pixel
    get() = Colors.BLANK

private data object DefaultRenderer : BaseRenderer() {
    override val glslVersion: String
        get() = "300 es"

    override fun beforeWindowCreation() = Unit

    override fun displayFrame() = Unit
}
