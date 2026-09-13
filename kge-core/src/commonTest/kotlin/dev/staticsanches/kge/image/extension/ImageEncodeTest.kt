package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.distinctSprite
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * `PngEncoder`/`JpegEncoder` through the `ImageService` seam: the encoded
 * payload is a caller-owned engine buffer. PNG round-trips losslessly; JPEG is
 * lossy and chroma-subsamples a 2x2, so only its signature, dimensions and
 * gross luminance structure are asserted. Wrapper lifecycle and leak ownership
 * are observed through `ResourceWrapper.cleaned`.
 */
class ImageEncodeTest :
    FunSpec({
        test("save as PNG produces a real PNG that round-trips losslessly") {
            distinctSprite().use { sprite ->
                ImageService.save(sprite, PngEncoder).use { encoded ->
                    encoded.bytes().take(8).map { it.toInt() and 0xFF } shouldBe PNG_SIGNATURE

                    ImageService.load(encoded, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { back ->
                        back.width shouldBe sprite.width
                        back.height shouldBe sprite.height
                        back.rowMajorPixels() shouldBe sprite.rowMajorPixels()
                    }
                }
            }
        }

        test("save as JPEG produces a real JPEG that round-trips its dimensions and luminance structure") {
            distinctSprite().use { sprite ->
                ImageService.save(sprite, JpegEncoder).use { encoded ->
                    encoded.bytes().take(3).map { it.toInt() and 0xFF } shouldBe JPEG_SIGNATURE

                    ImageService.load(encoded, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { back ->
                        back.width shouldBe sprite.width
                        back.height shouldBe sprite.height

                        // JPEG chroma-subsamples this 2x2, so hue does not
                        // survive on either backend; the source's gross
                        // luminance structure does (yellow > green > red >
                        // blue). The margins are floors, not exact values.
                        val luma = back.rowMajorPixels().map { it.luminance() }
                        val red = luma[RED_INDEX]
                        val green = luma[GREEN_INDEX]
                        val blue = luma[BLUE_INDEX]
                        val yellow = luma[YELLOW_INDEX]

                        (yellow - blue) shouldBeGreaterThan NOT_BLANK_MARGIN
                        yellow shouldBeGreaterThan red + LUMINANCE_MARGIN
                        yellow shouldBeGreaterThan green + LUMINANCE_MARGIN
                        blue shouldBeLessThan red - LUMINANCE_MARGIN
                        blue shouldBeLessThan green - LUMINANCE_MARGIN
                    }
                }
            }
        }

        test("the encode wrapper closes idempotently and fails fast on reuse") {
            distinctSprite().use { sprite ->
                val encoded = ImageService.save(sprite, PngEncoder)
                encoded.close()

                shouldThrow<IllegalStateException> { encoded.resource }

                encoded.close()
                encoded.cleaned shouldBe true
            }
        }

        test("an unclosed encode wrapper is not cleaned; a closed one is") {
            val allocated = mutableListOf<ResourceWrapper<ByteBuffer>>()
            captureAllocations(allocated)
            distinctSprite().use { sprite ->
                val leaked = ImageService.save(sprite, PngEncoder)
                val closed = ImageService.save(sprite, PngEncoder)
                closed.close()

                leaked.cleaned shouldBe false
                closed.cleaned shouldBe true

                leaked.close()
                leaked.cleaned shouldBe true
            }

            allocated.forEach { it.cleaned shouldBe true }
        }
    })

private val PNG_SIGNATURE = listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val JPEG_SIGNATURE = listOf(0xFF, 0xD8, 0xFF)

/** The [distinctSprite] palette order: red, green, blue, yellow (row-major). */
private const val RED_INDEX = 0
private const val GREEN_INDEX = 1
private const val BLUE_INDEX = 2
private const val YELLOW_INDEX = 3

/** Generous floors over the >=26/255 gaps observed on STB and the canvas. */
private const val LUMINANCE_MARGIN = 10
private const val NOT_BLANK_MARGIN = 30

/** Rec. 601 luma of [this]'s channels, in `[0, 255]`, for lossy comparisons. */
private fun Pixel.luminance(): Int = (r * 299 + g * 587 + b * 114) / 1000

private fun ResourceWrapper<ByteBuffer>.bytes(): ByteArray =
    ByteArray(resource.capacity()) { resource.byteAt(it).toByte() }

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
