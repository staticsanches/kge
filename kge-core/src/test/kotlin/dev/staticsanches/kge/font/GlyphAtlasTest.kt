package dev.staticsanches.kge.font

import dev.staticsanches.kge.engine.Window
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.WithOptionalRGBAData
import dev.staticsanches.kge.image.WithRGBAData
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.DecalInstance
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.test.KGEMockableRunner
import dev.staticsanches.kge.test.MockSPIFile
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(KGEMockableRunner::class)
@MockSPIFile(target = Renderer::class, implementation = GlyphAtlasTest.NoOpTextureRenderer::class)
class GlyphAtlasTest {
    @Test
    fun check() {
        TypeFace.load(GlyphAtlasTest::class.java.getResource("/fonts/Roboto/Roboto-Regular.ttf")!!).use { face ->
            val glyphs = TextShaper(face).append("Abc\t\tavvv", size = 13).append("bb\na a", size = 22).shape()
            glyphs.forEach(::println)
        }
    }

    class NoOpTextureRenderer : Renderer {
        private var textureID: Int = 1

        override fun beforeWindowCreation() = Unit

        override fun afterWindowCreation(window: Window) = Unit

        override fun prepareDrawing() = Unit

        override fun createTexture(
            filtered: Boolean,
            clamp: Boolean,
        ): Int = textureID++

        override fun deleteTexture(id: Int) = Unit

        override fun initializeTexture(
            id: Int,
            withOptionalRGBAData: WithOptionalRGBAData,
        ) = Unit

        override fun updateTexture(
            id: Int,
            withRGBAData: WithRGBAData,
        ) = Unit

        override fun readTexture(
            id: Int,
            withRGBAData: WithRGBAData,
        ) = Unit

        override fun applyTexture(id: Int) = Unit

        override fun clearBuffer(
            pixel: Pixel,
            depth: Boolean,
        ) = Unit

        override fun updateViewport(
            position: Int2D,
            size: Int2D,
        ) = Unit

        override fun displayFrame() = Unit

        override fun drawDecals(decals: List<DecalInstance>) = Unit

        override fun drawLayerQuad(
            offset: Float2D,
            scale: Float2D,
            tint: Pixel,
        ) = Unit

        override val servicePriority: Int
            get() = 0
    }
}
