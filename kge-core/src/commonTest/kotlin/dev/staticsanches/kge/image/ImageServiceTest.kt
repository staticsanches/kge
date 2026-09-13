package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The codec seam driven by in-memory fake codecs (no platform I/O): `load`
 * adopts the decoder's buffer zero-copy, `save` delegates to the encoder, and
 * the extension mechanism makes a decorator observable. Resource discipline is
 * pinned through `ResourceWrapper.cleaned` rather than the GC-driven leak
 * reporter, whose wasmJs callbacks leak across tests.
 */
class ImageServiceTest :
    FunSpec({
        test("load lands dimensions, sample mode and name, adopting the codec's wrapper") {
            val storage = rgbaBuffer(rgbaPixels)

            val sprite =
                ImageService.load(
                    Unit,
                    consumingDecoder(storage),
                    Pixmap.SampleMode.CLAMP,
                    "loaded",
                )

            sprite.use {
                it.width shouldBe 2
                it.height shouldBe 2
                it.sampleMode shouldBe Pixmap.SampleMode.CLAMP
                it.name shouldBe "loaded"
                it.rowMajorPixels() shouldBe rgbaPixels
                (it.buffer === storage.resource) shouldBe true
            }

            storage.cleaned shouldBe true
            shouldThrow<IllegalStateException> { storage.resource }
        }

        test("a decoder that consumes twice closes the extra wrapper and throws") {
            val first = rgbaBuffer(rgbaPixels)
            val extra = rgbaBuffer(rgbaPixels)

            val thrown =
                shouldThrow<IllegalStateException> {
                    ImageService.load(
                        Unit,
                        ImageService.Decoder { _, consume ->
                            consume(2, 2, first)
                            consume(2, 2, extra)
                        },
                        Pixmap.SampleMode.NORMAL,
                        null,
                    )
                }

            thrown.message shouldBe "the decoder consumed the data more than once"
            first.cleaned shouldBe true
            extra.cleaned shouldBe true
        }

        test("a decoder that never consumes throws") {
            shouldThrow<IllegalStateException> {
                ImageService.load(
                    Unit,
                    ImageService.Decoder { _, _ -> },
                    Pixmap.SampleMode.NORMAL,
                    null,
                )
            }
        }

        test("a decoder that throws after consume propagates and closes the adopted wrapper") {
            val storage = rgbaBuffer(rgbaPixels)

            val thrown =
                shouldThrow<IllegalStateException> {
                    ImageService.load(
                        Unit,
                        ImageService.Decoder { _, consume ->
                            consume(2, 2, storage)
                            throw IllegalStateException("decode failed")
                        },
                        Pixmap.SampleMode.NORMAL,
                        null,
                    )
                }

            thrown.message shouldBe "decode failed"
            storage.cleaned shouldBe true
        }

        test("a consume whose buffer size is wrong fails and closes the wrapper") {
            val wrongSize = BufferService.allocate(Int.SIZE_BYTES, null)

            shouldThrow<IllegalArgumentException> {
                ImageService.load(
                    Unit,
                    ImageService.Decoder { _, consume -> consume(2, 2, wrongSize) },
                    Pixmap.SampleMode.NORMAL,
                    null,
                )
            }

            wrongSize.cleaned shouldBe true
        }

        test("save returns exactly the encoder's value, passing the sprite through") {
            val sprite = distinctSprite()
            var encoded: Sprite? = null
            try {
                val payload =
                    ImageService.save(sprite) { source ->
                        encoded = source
                        "encoded"
                    }

                payload shouldBe "encoded"
                (encoded === sprite) shouldBe true
            } finally {
                sprite.close()
            }
        }

        test("a decorator overriding load is observable by the facade") {
            val original = ImageService.original
            var loadCalls = 0
            ImageService.override(
                object : ImageService {
                    override suspend fun <T> load(
                        data: T,
                        decoder: ImageService.Decoder<T>,
                        sampleMode: Pixmap.SampleMode,
                        name: String?,
                    ): Sprite {
                        loadCalls++
                        return original.load(data, decoder, sampleMode, name)
                    }
                },
            )

            val storage = rgbaBuffer(rgbaPixels)
            val sprite = ImageService.load(Unit, consumingDecoder(storage), Pixmap.SampleMode.NORMAL, null)

            sprite.use { it.rowMajorPixels() shouldBe rgbaPixels }
            loadCalls shouldBe 1
            storage.cleaned shouldBe true
        }

        test("a decorator overriding only save still loads through the default") {
            val original = ImageService.original
            var saveCalls = 0
            ImageService.override(
                object : ImageService {
                    override suspend fun <T> save(
                        sprite: Sprite,
                        encoder: ImageService.Encoder<T>,
                    ): T {
                        saveCalls++
                        return original.save(sprite, encoder)
                    }
                },
            )

            val storage = rgbaBuffer(rgbaPixels)
            val sprite = ImageService.load(Unit, consumingDecoder(storage), Pixmap.SampleMode.NORMAL, null)

            sprite.use {
                it.rowMajorPixels() shouldBe rgbaPixels
                ImageService.save(it) { _ -> "saved" } shouldBe "saved"
            }
            saveCalls shouldBe 1
            storage.cleaned shouldBe true
        }
    })

private val rgbaPixels: List<Pixel> =
    listOf(
        Colors.RED,
        Pixel.rgba(0, 255, 0, 128),
        Colors.BLUE,
        Pixel.rgba(255, 255, 0, 128),
    )

private fun rgbaBuffer(
    pixels: List<Pixel>,
    name: String? = null,
): ResourceWrapper<ByteBuffer> =
    BufferService.allocate(pixels.size * Int.SIZE_BYTES, name).letClosingIfFailed { wrapper ->
        pixels.forEachIndexed { index, pixel ->
            wrapper.resource.putInt(index * Int.SIZE_BYTES, pixel.nativeRGBA)
        }
        wrapper
    }

private fun consumingDecoder(pixels: ResourceWrapper<ByteBuffer>): ImageService.Decoder<Unit> =
    ImageService.Decoder { _, consume -> consume(2, 2, pixels) }
