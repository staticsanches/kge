package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The web-only payload behavior: the bytes live in one core engine buffer the
 * wasm engines copy out of, and the font's close releases it.
 */
@OptIn(KGESensitiveAPI::class)
class WebNativeFaceTest :
    FunSpec({
        test("loading stages one tracked engine buffer for the payload") {
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
                Font.load(Roboto.romanFont).use { font ->
                    // The payload is one core buffer while the font holds it.
                    allocations.size shouldBe 1
                    allocations.single().cleaned shouldBe false

                    val glyph = font.shape("A", 16).glyphs.single()
                    glyph.advance.x shouldBe 10.4375f
                }

                // The font's close releases the payload buffer it owns.
                allocations.single().cleaned shouldBe true
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
            }
        }
    })
