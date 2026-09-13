package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asEngineBuffer
import dev.staticsanches.kge.image.notAPngBytes
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyPngBytes
import dev.staticsanches.kge.image.tinyPngPixels
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * `BytesDecoder` through the `ImageService` seam: the engine buffer's bytes are
 * decoded by the platform backend and the decoded wrapper is adopted by the
 * returned sprite, while the caller owns the source wrapper. The
 * success/close canary is web-only — on the JVM the adopted wrapper is the STB
 * native buffer, not a `BufferService` allocation and so not capturable — so
 * the failure canary (a decode that allocates no engine buffer beyond the
 * caller-owned source) is the deterministic one on every target.
 */
class BytesImageDecodeTest :
    FunSpec({
        test("load decodes the fixture bytes into the expected 2x2 surface") {
            tinyPngBytes.asEngineBuffer().use { source ->
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                    sprite.sampleMode shouldBe Pixmap.SampleMode.NORMAL
                    sprite.name shouldBe null
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
            }
        }

        test("load lands the sampleMode and name on the decoded sprite") {
            tinyPngBytes.asEngineBuffer().use { source ->
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.CLAMP, "from-bytes").use { sprite ->
                    sprite.sampleMode shouldBe Pixmap.SampleMode.CLAMP
                    sprite.name shouldBe "from-bytes"
                }
            }
        }

        test("load throws on bytes that are not a decodable image") {
            notAPngBytes.asEngineBuffer().use { garbage ->
                shouldThrow<Throwable> {
                    ImageService.load(garbage, BytesDecoder, Pixmap.SampleMode.NORMAL, null)
                }
            }
        }

        test("the decoder does not close the caller-owned source") {
            val source = tinyPngBytes.asEngineBuffer()
            try {
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }

                source.cleaned shouldBe false
                source.resource.capacity() shouldBe tinyPngBytes.size
            } finally {
                source.close()
            }
        }

        test("a failed decode allocates no engine buffer beyond the caller-owned source") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)
            val source = notAPngBytes.asEngineBuffer()
            try {
                shouldThrow<Throwable> {
                    ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null)
                }
            } finally {
                source.close()
            }

            allocated.size shouldBe 1
            (allocated.single() === source) shouldBe true
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
