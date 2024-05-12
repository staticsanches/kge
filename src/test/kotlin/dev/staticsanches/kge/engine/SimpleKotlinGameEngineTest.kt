package dev.staticsanches.kge.engine

import dev.staticsanches.kge.configuration.Configuration
import dev.staticsanches.kge.engine.window.Window
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.DecalInstance
import dev.staticsanches.kge.renderer.GL11Renderer
import dev.staticsanches.kge.renderer.GL33Renderer
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.types.vector.Float2D
import dev.staticsanches.kge.types.vector.Int2D
import org.junit.jupiter.api.Timeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SimpleKotlinGameEngineTest {
    @Test
    @Timeout(10)
    fun checkRun_GL11() {
        val oldRenderer = DefaultRenderer.delegate
        try {
            DefaultRenderer.delegate = GL11Renderer
            SimpleKotlinGameEngine().start { }
        } finally {
            DefaultRenderer.delegate = oldRenderer
        }
    }

    @Test
    @Timeout(10)
    fun checkRun_G33() {
        val oldRenderer = DefaultRenderer.delegate
        try {
            DefaultRenderer.delegate = GL33Renderer
            SimpleKotlinGameEngine().start { }
        } finally {
            DefaultRenderer.delegate = oldRenderer
        }
    }

    private class SimpleKotlinGameEngine : KotlinGameEngine("Simple Test") {
        private val start = TimeSource.Monotonic.markNow()

        context(Window)
        override fun onUserUpdate(): Boolean {
            (0..<screenSize.x).forEach { x ->
                (0..<screenSize.y).forEach { y ->
                    draw(x, y, Pixel.rgba(randomComponent(), randomComponent(), randomComponent()))
                }
            }
            return (TimeSource.Monotonic.markNow() - start) < 2.seconds
        }

        companion object {
            private fun randomComponent(): Int = Random.nextInt(0, 256)
        }
    }
}

internal class DefaultRenderer : Renderer {
    override fun beforeWindowCreation() = delegate.beforeWindowCreation()

    context(Window)
    override fun afterWindowCreation() = delegate.afterWindowCreation()

    context(Window)
    override fun prepareDrawing() = delegate.prepareDrawing()

    context(Window)
    override fun createTexture(
        filtered: Boolean,
        clamp: Boolean,
    ): Int = delegate.createTexture(filtered, clamp)

    context(Window)
    override fun deleteTexture(id: Int) = delegate.deleteTexture(id)

    context(Window)
    override fun updateTexture(
        id: Int,
        sprite: Sprite,
    ) = delegate.updateTexture(id, sprite)

    context(Window)
    override fun readTexture(
        id: Int,
        sprite: Sprite,
    ) = delegate.readTexture(id, sprite)

    context(Window)
    override fun applyTexture(id: Int) = delegate.applyTexture(id)

    context(Window)
    override fun clearBuffer(
        pixel: Pixel,
        depth: Boolean,
    ) = delegate.clearBuffer(pixel, depth)

    context(Window)
    override fun updateViewport(
        position: Int2D,
        size: Int2D,
    ) = delegate.updateViewport(position, size)

    context(Window)
    override fun displayFrame() = delegate.displayFrame()

    context(Window)
    override fun drawDecal(decal: DecalInstance) = delegate.drawDecal(decal)

    context(Window)
    override fun drawLayerQuad(
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    ) = delegate.drawLayerQuad(offset, scale, tint)

    override val servicePriority: Int
        get() = Int.MIN_VALUE

    companion object {
        var delegate: Renderer = if (Configuration.useOpenGL11) GL11Renderer else GL33Renderer
    }
}
