@file:OptIn(KGESensitiveAPI::class)

package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.service.GLService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.testsupport.engine.installGl
import dev.staticsanches.kge.testsupport.gl.RecordedGLCall
import dev.staticsanches.kge.testsupport.gl.RecordingGLService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The carrier's recorder contract: the creation sequence, the per-box upload,
 * the write-once bookkeeping and the release chain, platform-gated.
 */
class GlyphAtlasGpuTest :
    FunSpec({
        test("the first decal creates the chart texture and uploads the placed box") {
            withRecordingGl { gl ->
                val font = Font.load(Roboto.romanFont)
                try {
                    val placed = placeGlyph(font, "A", 16)
                    gl.clear()

                    val decal = font.gpuAtlas(16).decalFor(placed)

                    gl.calls.map { it.name } shouldBe creationCallNames()
                    assertCreationParameters(gl.calls)
                    decal.sprite.name shouldBe chartOf(font, 16, placed).name
                    assertUpload(gl, placed, chartOf(font, 16, placed))
                } finally {
                    font.close()
                }
            }
        }

        test("a repeated placement uploads nothing") {
            withRecordingGl { gl ->
                val font = Font.load(Roboto.romanFont)
                try {
                    val placed = placeGlyph(font, "A", 16)
                    val gpu = font.gpuAtlas(16)
                    gpu.decalFor(placed)
                    gl.clear()

                    gpu.decalFor(placed)

                    gl.calls shouldBe emptyList()
                } finally {
                    font.close()
                }
            }
        }

        test("a newly placed glyph uploads only its own box and never re-specifies the texture") {
            withRecordingGl { gl ->
                val font = Font.load(Roboto.romanFont)
                try {
                    val first = placeGlyph(font, "A", 16)
                    val second = placeGlyph(font, "V", 16)
                    val gpu = font.gpuAtlas(16)
                    gpu.decalFor(first)
                    gl.clear()

                    gpu.decalFor(second)

                    gl.calls.map { it.name } shouldBe listOf("bindTexture", "texSubImage2D")
                    val upload = gl.calls.single { it.name == "texSubImage2D" }
                    upload.arguments.slice(2..5) shouldBe
                        listOf(second.source.x, second.source.y, second.size.x, second.size.y)
                    gl.calls.none { it.name == "texImage2D" } shouldBe true
                } finally {
                    font.close()
                }
            }
        }

        test("the upload packs every row at the 4-byte client stride the driver assumes") {
            withRecordingGl { gl ->
                val raster =
                    StubAtlasRaster(
                        mapOf(
                            1 to stubCoverage(6, 3, 1),
                            2 to stubCoverage(8, 2, 2),
                        ),
                    )
                val atlas = GlyphAtlas(16, raster::rasterize)
                val gpu = GlyphAtlasGpu(atlas)
                try {
                    val ragged = atlas.glyph(1) as AtlasGlyph.Placed
                    val aligned = atlas.glyph(2) as AtlasGlyph.Placed
                    // The ragged row is the defect case on the R8 path; on RGBA it
                    // is already 4-byte aligned, so both cases are the no-op one.
                    (ragged.size.x * coverageBytesPerPixel()) % CLIENT_ALIGNMENT shouldBe
                        if (coverageTextureIsSingleChannel) 2 else 0
                    (aligned.size.x * coverageBytesPerPixel()) % CLIENT_ALIGNMENT shouldBe 0

                    gpu.decalFor(ragged)
                    assertUpload(gl, ragged, atlas.charts[ragged.chartIndex])
                    gl.clear()

                    gpu.decalFor(aligned)
                    assertUpload(gl, aligned, atlas.charts[aligned.chartIndex])
                } finally {
                    gpu.close()
                    atlas.close()
                }
            }
        }

        test("close deletes the chart texture and releases the scratch buffer") {
            val allocations = mutableListOf<ResourceWrapper<ByteBuffer>>()
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> =
                        BufferService.original.allocate(sizeInBytes, name).also { allocations += it }
                },
            )
            try {
                withRecordingGl { gl ->
                    val font = Font.load(Roboto.romanFont)
                    val placed = placeGlyph(font, "A", 16)
                    allocations.clear()

                    font.gpuAtlas(16).decalFor(placed)
                    val scratch = allocations.single()
                    scratch.cleaned shouldBe false

                    font.close()

                    scratch.cleaned shouldBe true
                    val deleted = gl.calls.single { it.name == "deleteTexture" }
                    deleted.arguments.single() shouldBe gl.lastCreatedTexture
                }
            } finally {
                BufferService.override(BufferService.original)
            }
        }

        test("drawing a decal after Font.close fails fast") {
            withRecordingGl {
                val font = Font.load(Roboto.romanFont)
                val placed = placeGlyph(font, "A", 16)
                val gpu = font.gpuAtlas(16)

                font.close()

                shouldThrow<IllegalStateException> { gpu.decalFor(placed) }
                    .message shouldContain "released"
                shouldThrow<IllegalStateException> { font.gpuAtlas(16) }
                    .message shouldContain "released"
            }
        }

        test("a font that never draws a decal allocates no GPU object") {
            withRecordingGl { gl ->
                val font = Font.load(Roboto.romanFont)
                try {
                    placeGlyph(font, "A", 16)
                    font.gpuAtlas(16)

                    gl.calls shouldBe emptyList()
                } finally {
                    font.close()
                }
            }
        }

        test("a glyph on a later chart creates that chart's texture lazily") {
            withRecordingGl { gl ->
                val raster =
                    StubAtlasRaster(
                        mapOf(
                            1 to stubCoverage(512, 1, 1),
                            2 to stubCoverage(1, 512, 2),
                        ),
                    )
                val atlas = GlyphAtlas(16, raster::rasterize)
                val gpu = GlyphAtlasGpu(atlas)
                try {
                    val first = atlas.glyph(1) as AtlasGlyph.Placed
                    val second = atlas.glyph(2) as AtlasGlyph.Placed
                    first.chartIndex shouldBe 0
                    second.chartIndex shouldBe 1

                    val firstDecal = gpu.decalFor(first)
                    gl.calls.count { it.name == "createTexture" } shouldBe 1
                    gl.clear()

                    val secondDecal = gpu.decalFor(second)

                    gl.calls.count { it.name == "createTexture" } shouldBe 1
                    val upload = gl.calls.single { it.name == "texSubImage2D" }
                    upload.arguments.slice(2..5) shouldBe
                        listOf(second.source.x, second.source.y, second.size.x, second.size.y)
                    (firstDecal.sprite.name == secondDecal.sprite.name) shouldBe false
                } finally {
                    gpu.close()
                    atlas.close()
                }
                gl.calls.count { it.name == "deleteTexture" } shouldBe 2
            }
        }

        test("a larger box reallocates the scratch buffer and releases the previous one") {
            val allocations = mutableListOf<ResourceWrapper<ByteBuffer>>()
            val sizes = mutableListOf<Int>()
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> =
                        BufferService.original.allocate(sizeInBytes, name).also {
                            allocations += it
                            sizes += sizeInBytes
                        }
                },
            )
            try {
                withRecordingGl {
                    val raster =
                        StubAtlasRaster(
                            mapOf(
                                1 to stubCoverage(2, 2, 1),
                                2 to stubCoverage(8, 8, 2),
                            ),
                        )
                    val atlas = GlyphAtlas(16, raster::rasterize)
                    val gpu = GlyphAtlasGpu(atlas)
                    try {
                        val small = atlas.glyph(1) as AtlasGlyph.Placed
                        val large = atlas.glyph(2) as AtlasGlyph.Placed
                        allocations.clear()
                        sizes.clear()

                        gpu.decalFor(small)
                        val first = allocations.single()
                        first.cleaned shouldBe false

                        gpu.decalFor(large)

                        allocations.size shouldBe 2
                        sizes shouldBe listOf(scratchBytes(small), scratchBytes(large))
                        val second = allocations.last()
                        first.cleaned shouldBe true
                        second.cleaned shouldBe false

                        gpu.close()

                        second.cleaned shouldBe true
                    } finally {
                        atlas.close()
                    }
                }
            } finally {
                BufferService.override(BufferService.original)
            }
        }
    })

/** Installs the recording GL service and restores the engine default afterwards. */
private inline fun withRecordingGl(block: (RecordingGLService) -> Unit) {
    val gl = installGl()
    try {
        block(gl)
    } finally {
        GLService.override(GLService.original)
    }
}

/** Shapes [text] at [sizePx] and rasterizes its single glyph into the atlas. */
private suspend fun placeGlyph(
    font: Font,
    text: String,
    sizePx: Int,
): AtlasGlyph.Placed {
    val glyph = font.shape(text, sizePx).glyphs.single()
    return font.glyph(sizePx, glyph.glyphId) as AtlasGlyph.Placed
}

/** The chart [placed] landed on, reached through the atlas's public surface. */
private fun chartOf(
    font: Font,
    sizePx: Int,
    placed: AtlasGlyph.Placed,
): Sprite = checkNotNull(font.atlas(sizePx)) { "the glyph must have created an atlas" }.charts[placed.chartIndex]

private fun creationCallNames(): List<String> =
    buildList {
        add("createTexture")
        add("bindTexture")
        repeat(4) { add("texParameteri") }
        if (coverageTextureIsSingleChannel) repeat(4) { add("texParameteri") }
        add("texImage2D")
        add("bindTexture")
        add("texSubImage2D")
    }

private fun assertCreationParameters(calls: List<RecordedGLCall>) {
    val expectedParameters =
        buildList {
            add(listOf(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST))
            add(listOf(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST))
            add(listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE))
            add(listOf(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE))
            if (coverageTextureIsSingleChannel) {
                add(listOf(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_R, GL.ONE))
                add(listOf(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_G, GL.ONE))
                add(listOf(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_B, GL.ONE))
                add(listOf(GL.TEXTURE_2D, GL.TEXTURE_SWIZZLE_A, GL.RED))
            }
        }
    calls.filter { it.name == "texParameteri" }.map { it.arguments } shouldBe expectedParameters

    calls.single { it.name == "texImage2D" }.arguments shouldBe
        listOf(
            GL.TEXTURE_2D,
            0,
            coverageInternalFormat(),
            512,
            512,
            0,
            coverageUploadFormat(),
            GL.UNSIGNED_BYTE,
            null,
        )
}

private fun assertUpload(
    gl: RecordingGLService,
    placed: AtlasGlyph.Placed,
    chart: Sprite,
) {
    val upload = gl.calls.single { it.name == "texSubImage2D" }
    upload.arguments.slice(0..1) shouldBe listOf(GL.TEXTURE_2D, 0)
    upload.arguments.slice(2..5) shouldBe
        listOf(placed.source.x, placed.source.y, placed.size.x, placed.size.y)
    upload.arguments.slice(6..7) shouldBe listOf(coverageUploadFormat(), GL.UNSIGNED_BYTE)

    val bytesPerPixel = coverageBytesPerPixel()
    val rowStride = clientRowStride(placed.size.x)
    val buffer = upload.arguments[8] as ByteBuffer
    val actual =
        IntArray(placed.size.x * placed.size.y) { index ->
            val offset = (index / placed.size.x) * rowStride + (index % placed.size.x) * bytesPerPixel
            if (coverageTextureIsSingleChannel) {
                buffer.byteAt(offset)
            } else {
                buffer.getInt(offset)
            }
        }
    val expected =
        IntArray(placed.size.x * placed.size.y) { index ->
            val pixel =
                chart.get(
                    placed.source.x + index % placed.size.x,
                    placed.source.y + index / placed.size.x,
                )
            if (coverageTextureIsSingleChannel) pixel.a else pixel.nativeRGBA
        }
    actual shouldBe expected
}

private fun coverageInternalFormat(): Int = if (coverageTextureIsSingleChannel) GL.R8 else GL.RGBA

private fun coverageUploadFormat(): Int = if (coverageTextureIsSingleChannel) GL.RED else GL.RGBA

private fun coverageBytesPerPixel(): Int = if (coverageTextureIsSingleChannel) 1 else Int.SIZE_BYTES

/** The client row stride the driver's default 4-byte unpack alignment imposes. */
private fun clientRowStride(width: Int): Int =
    (width * coverageBytesPerPixel() + CLIENT_ALIGNMENT - 1) and (CLIENT_ALIGNMENT - 1).inv()

private fun scratchBytes(placed: AtlasGlyph.Placed): Int = clientRowStride(placed.size.x) * placed.size.y

private const val CLIENT_ALIGNMENT = 4

private class StubAtlasRaster(
    private val coverages: Map<Int, GlyphCoverage>,
) {
    fun rasterize(glyphId: Int): GlyphCoverage = coverages.getValue(glyphId)
}

private fun stubCoverage(
    width: Int,
    height: Int,
    seed: Int,
): GlyphCoverage =
    GlyphCoverage(
        width = width,
        height = height,
        bearing = Int2D(0, -height),
        coverage = ByteArray(width * height) { index -> ((index * 7 + seed) % 251).toByte() },
    )
