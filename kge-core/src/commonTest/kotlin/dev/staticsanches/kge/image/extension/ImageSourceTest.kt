package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.distinctSprite
import dev.staticsanches.kge.image.notAPngBytes
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyPngBase64
import dev.staticsanches.kge.image.tinyPngPixels
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlin.io.encoding.Base64

/**
 * [Base64Decoder]/[Base64PngEncoder] through the [ImageService] seam. The
 * base64 payload is not a resource; the decoded intermediate buffer the
 * decoder allocates is released on success and on failure, observed
 * deterministically through [BufferService] and [ResourceWrapper.cleaned] (no
 * GC-driven leak reporter, whose wasmJs callbacks leak across tests).
 */
class ImageSourceTest :
    FunSpec({
        test("load decodes the base64 fixture into the expected 2x2 surface") {
            ImageService.load(tinyPngBase64, Base64Decoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                sprite.width shouldBe 2
                sprite.height shouldBe 2
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }
        }

        test("save as base64 PNG decodes back to the same pixels") {
            distinctSprite().use { sprite ->
                val payload = ImageService.save(sprite, Base64PngEncoder)
                payload.shouldNotBeEmpty()

                ImageService.load(payload, Base64Decoder, Pixmap.SampleMode.NORMAL, null).use { back ->
                    back.rowMajorPixels() shouldBe sprite.rowMajorPixels()
                }
            }
        }

        test("the base64 payload is a real PNG") {
            distinctSprite().use { sprite ->
                Base64.Default
                    .decode(ImageService.save(sprite, Base64PngEncoder))
                    .take(8)
                    .map { it.toInt() and 0xFF } shouldBe
                    listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            }
        }

        test("the decoder frees its intermediate buffer on success, with no leak") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)

            ImageService.load(tinyPngBase64, Base64Decoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }

            allocated.forEach { it.cleaned shouldBe true }
        }

        test("a failed decode frees its intermediate buffer, with no leak") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)

            shouldThrow<Throwable> {
                ImageService.load(
                    Base64.Default.encode(notAPngBytes),
                    Base64Decoder,
                    Pixmap.SampleMode.NORMAL,
                    null,
                )
            }

            allocated.forEach { it.cleaned shouldBe true }
        }
    })

private fun captureAllocations(allocated: MutableList<ResourceWrapper<ByteBuffer>>) {
    BufferService.override(
        object : BufferService {
            override fun allocate(
                sizeInBytes: Int,
                name: String?,
            ): ResourceWrapper<ByteBuffer> = BufferService.original.allocate(sizeInBytes, name).also { allocated += it }
        },
    )
}
