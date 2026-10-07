package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The web-only payload behavior: the bytes live in one core engine buffer the
 * wasm engines copy out of, and the loading scope's close releases it.
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
                ResourceScope().use { scope ->
                    KGETtfFontService.createResources(scope, Roboto.romanFont)

                    // The payload is one core buffer while the family holds it.
                    allocations.size shouldBe 1
                    allocations.single().cleaned shouldBe false
                }

                // The scope's close releases the payload buffer the family owned.
                allocations.single().cleaned shouldBe true

                withRobotoFace { face ->
                    val glyph = face.shape("A".toCodePoints(), 16).single()
                    glyph.advance.x shouldBe 10.4375f
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
            }
        }
    })
