package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

/**
 * The resource contract on the font: close is idempotent, every use after close
 * fails fast and the face's close releases the payload it was built from. On
 * web, close drops the native references only.
 */
@OptIn(KGESensitiveAPI::class)
class FontResourceTest :
    FunSpec({
        test("close is idempotent") {
            val font = Font.load(Roboto.variableFont)

            font.close()
            font.close()
        }

        test("shaping after close fails fast") {
            val font = Font.load(Roboto.variableFont)
            font.close()

            val failure = shouldThrow<IllegalStateException> { font.shape("A", 16) }
            failure.message shouldContain "released"
        }

        test("closing the font releases the payload it allocated") {
            var payload: ResourceWrapper<ByteBuffer>? = null
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> =
                        BufferService.original.allocate(sizeInBytes, name).also { payload = it }
                },
            )
            try {
                val font = Font.load(Roboto.variableFont)
                val allocated = checkNotNull(payload) { "load must allocate the payload" }

                // Web copies into wasm memory and releases the staging buffer
                // during open; JVM holds it until close. Either way the font's
                // close leaves no payload behind.
                font.close()

                allocated.cleaned shouldBe true
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
            }
        }

        test("each pixel size gets its own lazily created atlas") {
            Font.load(Roboto.variableFont).use { font ->
                val glyphs = font.shape("A", 16).glyphs
                val glyphId = glyphs.single().glyphId

                font.atlas(16) shouldBe null
                font.glyph(16, glyphId)
                font.glyph(32, glyphId)

                font.atlas(16) shouldNotBe null
                font.atlas(32) shouldNotBe null
                font.atlas(24) shouldBe null
                (font.atlas(16) !== font.atlas(32)) shouldBe true
            }
        }

        test("closing a rasterized font releases the payload and its charts") {
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
                val font = Font.load(Roboto.variableFont)
                val glyphs = font.shape("A", 16).glyphs
                val glyphId = glyphs.single().glyphId
                font.glyph(16, glyphId)

                // the payload plus the one chart the glyph landed on
                allocations.size shouldBe 2
                val chart = checkNotNull(font.atlas(16)) { "rasterizing must create the atlas" }.charts.single()

                font.close()

                allocations.forEach { it.cleaned shouldBe true }
                shouldThrow<IllegalStateException> { chart.get(0, 0) }
            } finally {
                BufferService.override(BufferService.original)
            }
        }

        test("rasterizing after close fails fast") {
            val font = Font.load(Roboto.variableFont)
            font.close()

            val failure = shouldThrow<IllegalStateException> { font.glyph(16, 0) }
            failure.message shouldContain "released"
        }

        test("a non-positive raster size fails fast") {
            Font.load(Roboto.variableFont).use { font ->
                shouldThrow<IllegalArgumentException> { font.glyph(0, 0) }
            }
        }

        test("shaping is unchanged after rasterizing at the same and a different size") {
            Font.load(Roboto.variableFont).use { font ->
                val before = font.shape("AV To Wave 123", 16)
                val glyphId = before.glyphs.first().glyphId

                font.glyph(16, glyphId)
                font.glyph(32, glyphId)

                font.shape("AV To Wave 123", 16) shouldBe before
            }
        }
    })
