package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.notAPngBytes
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyPngBase64
import dev.staticsanches.kge.image.tinyPngPixels
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.encoding.Base64

/**
 * The browser fetch decoder over a data: URL built from the fixture bytes. The
 * browser `fetch` serves data: URLs, so the decode path is reachable without a
 * server.
 */
class FetchDecoderTest :
    FunSpec({
        test("load decodes the fixture PNG from a data: URL") {
            val dataUrl = "data:image/png;base64,$tinyPngBase64"

            ImageService.load(dataUrl, FetchDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                sprite.width shouldBe 2
                sprite.height shouldBe 2
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }
        }

        test("a failed decode of a data: URL frees the read buffer, with no leak") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)

            val dataUrl = "data:application/octet-stream;base64,${Base64.Default.encode(notAPngBytes)}"
            shouldThrow<Throwable> {
                ImageService.load(dataUrl, FetchDecoder, Pixmap.SampleMode.NORMAL, null)
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
