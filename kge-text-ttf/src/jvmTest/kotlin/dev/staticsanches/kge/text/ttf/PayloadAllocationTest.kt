package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceScope
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.text.fontPx
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The JVM payload-allocation pin: one engine buffer per payload, shared by every
 * lease over its face, and the family's close releases all of them.
 */
@OptIn(KGESensitiveAPI::class)
class PayloadAllocationTest :
    FunSpec({
        test("a family stages one engine buffer per payload and its close releases each one") {
            val allocations = mutableListOf<ResourceWrapper<ByteBuffer>>()
            BufferService.override(recordingAllocations(allocations))
            try {
                ResourceScope().use { scope ->
                    val family = KGETtfFontService.createResources(scope, robotoFontBytes(), robotoItalicBytes())
                    allocations.size shouldBe 2

                    family.defaultFace.font(scope, 16.fontPx)
                    family.defaultFace.font(scope, 16.fontPx)
                    allocations.size shouldBe 2

                    family.close()

                    allocations.forEach { it.cleaned shouldBe true }
                }
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                BufferService.override(BufferService.original)
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
