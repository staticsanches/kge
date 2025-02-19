package dev.staticsanches.kge.font

import dev.staticsanches.kge.configuration.Configuration
import dev.staticsanches.kge.font.GlyphAtlas.Chart.Row.Glyph
import dev.staticsanches.kge.font.GlyphAtlas.GlyphInfo
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.IntColorComponent
import dev.staticsanches.kge.image.PartialDecal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.WithOptionalRGBAData
import dev.staticsanches.kge.image.WithRGBAData
import dev.staticsanches.kge.image.pixelmap.buffer.PixelBuffer
import dev.staticsanches.kge.image.service.PixelBufferService
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.FloatOneByOne
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.IntZeroByZero
import dev.staticsanches.kge.math.vector.by
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.Texture
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.utils.invokeForAllRemoving
import org.lwjgl.util.freetype.FreeType
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.use

internal class GlyphAtlas(
    private val face: TypeFace,
    private val fontSize: Int,
) : KGEInternalResource {
    private val chartDimension: Int2D
    private val chartUVScale: Float2D

    private val rowThreshold = Configuration.glyphChartRowHeightThreshold
    private val rowIncrease = Configuration.glyphChartRowHeightIncrease

    private val charts = mutableListOf<Chart>()
    private val glyphByIndex = ConcurrentHashMap<Int, GlyphInfo>()

    val baselineToBaseline: FPU

    init {
        val chartWidthHeight = Configuration.glyphChartWidthHeight
        chartDimension = chartWidthHeight by chartWidthHeight
        chartUVScale = FloatOneByOne / chartDimension

        baselineToBaseline =
            face.withSize(fontSize) {
                FPU(
                    it.size()?.metrics()?.height()
                        ?: throw RuntimeException("[$face] Unable to acquire baseline to baseline distance"),
                )
            }
    }

    fun glyph(index: Int): GlyphInfo = glyphByIndex.computeIfAbsent(index, ::loadGlyphByIndex)

    override fun close() =
        synchronized(charts) {
            try {
                charts.invokeForAllRemoving { it.close() }
            } finally {
                glyphByIndex.clear()
            }
        }

    override fun toString(): String = "Glyph Atlas of $face (${fontSize}px)"

    private fun loadGlyphByIndex(glyphIndex: Int): GlyphInfo =
        face.withSize(fontSize) { sizedFace ->
            FreeType
                .FT_Load_Glyph(sizedFace, glyphIndex, FreeType.FT_LOAD_RENDER)
                .handleFTError { code, errorString ->
                    "[$sizedFace] Unable to load glyph with index $glyphIndex (error code $code): $errorString"
                }

            val glyph = sizedFace.glyph() ?: throw RuntimeException("[$sizedFace] Unable to access glyph slot")
            val advance = FPU(glyph.advance().x()) by FPU(glyph.advance().y())

            val bitmap = glyph.bitmap()
            val width = bitmap.width()
            val height = bitmap.rows()
            if (width <= 0 || height <= 0) {
                return GlyphInfo(glyphIndex, advance, FPUZeroByZero, null)
            }

            val buffer =
                bitmap.buffer(height * width)
                    ?: throw RuntimeException(
                        "[$sizedFace] Unable to retrieve buffer of glyph with index ${glyph.glyph_index()}",
                    )

            val numberOfGrays = bitmap.num_grays().toInt()
            val toAlpha: (byte: Byte) -> IntColorComponent =
                if (numberOfGrays == 256) {
                    { byte -> byte.toUByte().toInt() }
                } else {
                    { byte -> (byte.toUByte().toInt() * 256) / numberOfGrays }
                }
            val alphas: (x: Int, y: Int) -> IntColorComponent =
                if (bitmap.pitch() > 0) {
                    { x, y -> toAlpha(buffer[y * width + x]) }
                } else {
                    { x, y -> toAlpha(buffer[(height - y) * width + x]) }
                }

            val decal =
                synchronized(charts) {
                    charts
                        .asSequence()
                        .flatMap { it.rows }
                        .firstOrNull { it.fits(width, height) }
                        ?.let { return@synchronized it.add(glyphIndex, width, height, alphas) }

                    val newRowHeight = (height * rowIncrease).toInt()
                    charts
                        .asSequence()
                        .map { it.createRowIfPossible(newRowHeight) }
                        .filter { it != null }
                        .firstOrNull()
                        ?.let { return@synchronized it.add(glyphIndex, width, height, alphas) }

                    val newChart = Chart()
                    charts += newChart
                    return@synchronized checkNotNull(newChart.createRowIfPossible(newRowHeight)) {
                        "[$sizedFace] It is impossible to accommodate a glyph with height $height"
                    }.add(glyphIndex, width, height, alphas)
                }

            return GlyphInfo(
                index = glyphIndex,
                advance = advance,
                bearing = FPU(glyph.bitmap_left() shl 6) by -FPU(glyph.bitmap_top() shl 6),
                decal = decal,
            )
        }

    inner class GlyphInfo(
        val index: Int,
        val advance: FPU2D,
        val bearing: FPU2D,
        val decal: PartialDecal?,
    ) {
        override fun toString(): String = "Glyph $index of $face (${fontSize}px)"
    }

    private inner class Chart :
        WithOptionalRGBAData,
        KGEResource {
        override val size: Int2D by ::chartDimension
        override val lowerBoundInclusive: Int2D by ::IntZeroByZero
        override val upperBoundExclusive: Int2D by ::chartDimension

        val rows = mutableListOf<Row>()
        private val nextYStartInclusive: Int
            get() = if (rows.isEmpty()) 0 else rows.last().let { row -> row.height + row.yOffset }
        private val texture = Texture("Glyph Chart of $face (${fontSize}px)", false, true)

        init {
            applyAndCloseIfFailed { Renderer.initializeTexture(texture.id, this) }
        }

        fun createRowIfPossible(height: Int): Row? {
            val yStartInclusive = nextYStartInclusive
            if (yStartInclusive + height > chartDimension.y) return null
            val row = Row(yStartInclusive, height)
            rows += row
            return row
        }

        override fun close() = texture.close()

        override fun toString(): String = texture.toString()

        inner class Row(
            val yOffset: Int,
            val height: Int,
        ) {
            private var xStart = 0

            fun fits(
                glyphWidth: Int,
                glyphHeight: Int,
            ): Boolean =
                glyphHeight <= height &&
                    glyphWidth <= size.x - xStart &&
                    glyphHeight >= rowThreshold * height

            fun add(
                glyphIndex: Int,
                glyphWidth: Int,
                glyphHeight: Int,
                alphas: (x: Int, y: Int) -> IntColorComponent,
            ): Glyph {
                val glyph = Glyph(glyphIndex, xStart by yOffset, glyphWidth by glyphHeight, alphas)
                xStart += glyphWidth
                return glyph
            }

            private inner class Glyph(
                private val index: Int,
                position: Int2D,
                override val size: Int2D,
                alphas: (x: Int, y: Int) -> IntColorComponent,
            ) : PartialDecal,
                WithRGBAData {
                private var internalBuffer: ByteBuffer? = null
                override val rgbaData: ByteBuffer
                    get() = internalBuffer ?: throw IllegalStateException("Glyph already initialized")

                override val id: Int by texture::id
                override val textureDimension: Int2D by ::chartDimension
                override val uvScale: Float2D by ::chartUVScale
                override val lowerBoundInclusive: Int2D = position
                override val upperBoundExclusive: Int2D
                    get() = lowerBoundInclusive + size

                init {
                    val (width, height) = size
                    PixelBufferService.create(PixelBuffer.Type.RGBA, width, height).use { pixelBuffer ->
                        for (y in 0..<height) {
                            for (x in 0..<width) {
                                val alpha = alphas(x, y)
                                pixelBuffer.uncheckedSet(
                                    x,
                                    y,
                                    when (alpha) {
                                        0 -> Colors.BLANK
                                        255 -> Colors.WHITE
                                        else -> Pixel.rgba(255, 255, 255, alpha)
                                    },
                                )
                            }
                        }
                        try {
                            internalBuffer = pixelBuffer.rgbaData
                            Renderer.updateTexture(id, this)
                        } finally {
                            internalBuffer = null
                        }
                    }
                }

                override fun close() =
                    throw RuntimeException("A glyph can not be closed. If necessary, close the atlas")

                override fun toString(): String = "Glyph $index of $face (${fontSize}px)"
            }
        }
    }
}
