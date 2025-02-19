package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.configuration.Configuration
import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.WithOptionalRGBAData
import dev.staticsanches.kge.image.WithRGBAData
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
        withOptionalRGBAData: WithOptionalRGBAData,
    )

    fun updateTexture(
        id: Int,
        withRGBAData: WithRGBAData,
    )

    fun readTexture(
        id: Int,
        withRGBAData: WithRGBAData,
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
