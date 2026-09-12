package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.notAPngBytes
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyPngPixels
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The JVM URL decoder over the classpath resource PNG (the same bytes as the
 * commonTest fixture). The blocking stream read runs on `Dispatchers.IO`
 * inside the decoder.
 */
class UrlDecoderJvmTest :
    FunSpec({
        test("load decodes the classpath resource PNG") {
            val resource = checkNotNull(javaClass.getResource("/tiny.png"))

            ImageService.load(resource, UrlDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                sprite.width shouldBe 2
                sprite.height shouldBe 2
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }
        }

        test("a failed decode of a file: URL frees the read buffer, with no leak") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)
            val temp = File.createTempFile("kge-url-decoder", ".bin")
            try {
                temp.writeBytes(notAPngBytes)

                shouldThrow<Throwable> {
                    ImageService.load(temp.toURI().toURL(), UrlDecoder, Pixmap.SampleMode.NORMAL, null)
                }

                allocated.forEach { it.cleaned shouldBe true }
            } finally {
                temp.delete()
            }
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
