package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The browser-native codec primitive: decode through `createImageBitmap` +
 * canvas `getImageData`, encode through `putImageData` + `toDataURL`.
 *
 * Decode allocates its RGBA wrapper through [BufferService], fills it and
 * *hands it to* `consume` (ownership transferred — the codec does not close it
 * on the consume path). The hand-over is pinned by the deterministic
 * [ResourceWrapper.cleaned] state; the GC-driven leak reporter is deliberately
 * not used, because on wasmJs its FinalizationRegistry callbacks from earlier
 * tests' intentionally-leaked wrappers fire during a later test and pollute the
 * report.
 */
class WebImageCodecTest :
    FunSpec({
        test("decode yields the fixture surface and hands over its buffer wrapper") {
            val source = tinyPngBytes.asEngineBuffer()
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> =
                        BufferService.original.allocate(sizeInBytes, name).also { allocated += it }
                },
            )

            var width = -1
            var height = -1
            var pixels: List<Pixel>? = null
            var storage: ResourceWrapper<ByteBuffer>? = null
            try {
                WebImageCodec.decode(source.resource) { w, h, buffer ->
                    width = w
                    height = h
                    storage = buffer
                    pixels = (0 until w * h).map { Pixel.fromNativeRGBA(buffer.resource.getInt(it * Int.SIZE_BYTES)) }
                }
            } finally {
                source.close()
            }

            width shouldBe 2
            height shouldBe 2
            pixels shouldBe tinyPngPixels

            (storage === allocated.single()) shouldBe true
            allocated.single().cleaned shouldBe false
            allocated.single().close()
            allocated.single().cleaned shouldBe true
            shouldThrow<IllegalStateException> { allocated.single().resource }
        }

        test("encodePng writes a real PNG that decodes back to the same pixels") {
            val sprite = distinctSprite()
            try {
                val bytes = WebImageCodec.encodePng(sprite)

                bytes.copyOfRange(0, PNG_SIGNATURE.size).toList() shouldBe PNG_SIGNATURE
                decodedSurface(bytes).pixels shouldBe sprite.rowMajorPixels()
            } finally {
                sprite.close()
            }
        }

        test("encodeJpeg writes a real JPEG with the sprite dimensions") {
            val sprite = distinctSprite()
            try {
                val bytes = WebImageCodec.encodeJpeg(sprite)

                bytes.copyOfRange(0, JPEG_SIGNATURE.size).toList() shouldBe JPEG_SIGNATURE
                val surface = decodedSurface(bytes)
                surface.width shouldBe 2
                surface.height shouldBe 2
            } finally {
                sprite.close()
            }
        }
    })

private val PNG_SIGNATURE =
    listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).map { it.toByte() }

private val JPEG_SIGNATURE = listOf(0xFF, 0xD8, 0xFF).map { it.toByte() }

private data class DecodedSurface(
    val width: Int,
    val height: Int,
    val pixels: List<Pixel>,
)

private suspend fun decodedSurface(bytes: ByteArray): DecodedSurface {
    val source = bytes.asEngineBuffer()
    try {
        var width = -1
        var height = -1
        var pixels: List<Pixel>? = null
        WebImageCodec.decode(source.resource) { w, h, buffer ->
            width = w
            height = h
            pixels = (0 until w * h).map { Pixel.fromNativeRGBA(buffer.resource.getInt(it * Int.SIZE_BYTES)) }
            buffer.close()
        }
        return DecodedSurface(width, height, pixels!!)
    } finally {
        source.close()
    }
}
