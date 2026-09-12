package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asEngineBuffer
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyPngBytes
import dev.staticsanches.kge.image.tinyPngPixels
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Web-only adoption/close canary for [BytesDecoder]: the browser backend's
 * decode wrapper is a [BufferService] allocation (`WebImageCodec.decode`), so
 * [ImageService.load] adopting it and the sprite close releasing it are both
 * observable through [ResourceWrapper.cleaned]. On the JVM the adopted wrapper
 * is the STB native buffer, which is not a [BufferService] allocation — see
 * `BytesImageDecodeTest` for that platform limitation.
 */
class BytesImageDecodeBrowserTest :
    FunSpec({
        test("load adopts the web decode wrapper and the sprite close releases it") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)
            val source = tinyPngBytes.asEngineBuffer()
            try {
                val sprite = ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null)
                val decodeBuffer = allocated.single { it !== source }
                decodeBuffer.cleaned shouldBe false
                try {
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                } finally {
                    sprite.close()
                }

                decodeBuffer.cleaned shouldBe true
            } finally {
                source.close()
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
