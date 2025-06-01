package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.resource.ResourceWrapper

interface Renderer : KGEExtensibleService {
    fun beforeWindowCreation()

    fun afterWindowCreation(window: Window)

    fun prepareDrawing()

    fun drawLayerQuad(
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    )

    fun drawDecals(dis: List<DecalInstance>)

    fun createTexture(
        name: String?,
        filtered: Boolean,
        clamp: Boolean,
        size: Int2D,
    ): ResourceWrapper<GLTexture>

    fun applyTexture(texture: GLTexture)

    fun updateTexture(
        texture: GLTexture,
        sprite: Sprite,
    )

    fun readTexture(
        texture: GLTexture,
        sprite: Sprite,
    )

    fun clearBuffer(
        color: Pixel = defaultClearBufferColor,
        depth: Boolean,
    )

    fun updateViewport(
        position: Int2D,
        size: Int2D,
    )

    fun displayFrame()

    companion object : Renderer by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalRendererImplementation {
        var defaultClearBufferColor: Pixel = originalDefaultClearBufferColor
    }
}

expect val originalRendererImplementation: Renderer
expect val originalDefaultClearBufferColor: Pixel
