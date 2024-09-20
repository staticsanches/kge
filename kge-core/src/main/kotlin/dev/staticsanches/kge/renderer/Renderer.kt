package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.configuration.Configuration
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.spi.KGESPIExtensible

interface Renderer : KGESPIExtensible {
    fun beforeWindowCreation()

    fun afterWindowCreation(window: Window)

    fun prepareDrawing()

    fun createTexture(
        filtered: Boolean,
        clamp: Boolean,
    ): Int

    fun deleteTexture(id: Int)

    fun initializeTexture(
        id: Int,
        sprite: Sprite,
    )

    fun updateTexture(
        id: Int,
        sprite: Sprite,
    )

    fun readTexture(
        id: Int,
        sprite: Sprite,
    )

    fun applyTexture(id: Int)

    fun clearBuffer(
        pixel: Pixel,
        depth: Boolean,
    )

    fun updateViewport(
        position: Int2D,
        size: Int2D,
    )

    fun displayFrame()

    fun drawDecals(decals: List<DecalInstance>)

    fun drawLayerQuad(
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    )

    companion object : Renderer by KGESPIExtensible.getOptionalWithHigherPriority()
        ?: if (Configuration.useOpenGL11) GL11Renderer else GL33Renderer
}
