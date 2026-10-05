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
 * fails fast, and the font releases the atlas, the native face and then the payload.
 */
@OptIn(KGESensitiveAPI::class)
class FontResourceTest :
    FunSpec({
        test("close is idempotent") {
            val font = Font.load(Roboto.romanFont)

            font.close()
            font.close()
        }

        test("shaping after close fails fast") {
            val font = Font.load(Roboto.romanFont)
            font.close()

            val failure = shouldThrow<IllegalStateException> { font.shape("A", 16) }
            failure.message shouldContain "released"
        }

        test("closing the font releases the engine payload it allocated") {
            val allocations = mutableListOf<ResourceWrapper<ByteBuffer>>()
            BufferService.override(recordingAllocations(allocations))
            try {
                val font = Font.load(Roboto.romanFont)

                // One payload buffer on both platforms; a per-face copy would show as a second.
                allocations.size shouldBe 1

                font.close()

                allocations.forEach { it.cleaned shouldBe true }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
            }
        }

        test("each pixel size gets its own lazily created atlas") {
            Font.load(Roboto.romanFont).use { font ->
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
            BufferService.override(recordingAllocations(allocations))
            try {
                val font = Font.load(Roboto.romanFont)
                val glyphs = font.shape("A", 16).glyphs
                val glyphId = glyphs.single().glyphId
                // The one payload buffer, allocated on both platforms.
                val payloadAllocations = allocations.size
                payloadAllocations shouldBe 1

                font.glyph(16, glyphId)

                // Rasterizing adds exactly the one chart the glyph landed on
                allocations.size shouldBe payloadAllocations + 1
                val chart = checkNotNull(font.atlas(16)) { "rasterizing must create the atlas" }.charts.single()

                font.close()

                allocations.forEach { it.cleaned shouldBe true }
                shouldThrow<IllegalStateException> { chart.get(0, 0) }
            } finally {
                BufferService.override(BufferService.original)
            }
        }

        test("rasterizing after close fails fast") {
            val font = Font.load(Roboto.romanFont)
            font.close()

            val failure = shouldThrow<IllegalStateException> { font.glyph(16, 0) }
            failure.message shouldContain "released"
        }

        test("a non-positive raster size fails fast") {
            Font.load(Roboto.romanFont).use { font ->
                shouldThrow<IllegalArgumentException> { font.glyph(0, 0) }
            }
        }

        test("shaping is unchanged after rasterizing at the same and a different size") {
            Font.load(Roboto.romanFont).use { font ->
                val before = font.shape("AV To Wave 123", 16)
                val glyphId = before.glyphs.first().glyphId

                font.glyph(16, glyphId)
                font.glyph(32, glyphId)

                font.shape("AV To Wave 123", 16) shouldBe before
            }
        }
    })

/** Records every engine buffer the module allocates while the override is active. */
private fun recordingAllocations(allocations: MutableList<ResourceWrapper<ByteBuffer>>): BufferService =
    object : BufferService {
        override fun allocate(
            sizeInBytes: Int,
            name: String?,
        ): ResourceWrapper<ByteBuffer> = BufferService.original.allocate(sizeInBytes, name).also { allocations += it }
    }
