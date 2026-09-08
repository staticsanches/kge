package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.LeakReporterService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.onCollectionObserved
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * S5 load: [PngService.load] composes [PngSource.read] and [PngService.decode],
 * and the read wrapper is always closed — on a decode failure too, with no
 * leak report. The engine-provided common source is the base64 one.
 */
class PngLoadTest :
    FunSpec({
        test("load reads a base64 source and returns the fixture surface") {
            PngService
                .load(PngSource.base64(tinyPngBase64))
                .use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
        }

        test("load lands the sampleMode and name on the loaded sprite") {
            PngService
                .load(PngSource.base64(tinyPngBase64), Pixmap.SampleMode.CLAMP, "from-source")
                .use { sprite ->
                    sprite.sampleMode shouldBe Pixmap.SampleMode.CLAMP
                    sprite.toString() shouldContain "from-source"
                }
        }

        test("load closes the read wrapper on success") {
            val wrapper = tinyPngBytes.asEngineBuffer()
            val source =
                object : PngSource {
                    override suspend fun read(): ResourceWrapper<ByteBuffer> = wrapper
                }

            PngService.load(source).use { sprite ->
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }

            wrapper.cleaned shouldBe true
        }

        test("load closes the read wrapper when decode fails, with no leak report") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )

            val wrapper = notAPngBytes.asEngineBuffer()
            val source =
                object : PngSource {
                    override suspend fun read(): ResourceWrapper<ByteBuffer> = wrapper
                }

            shouldThrow<Throwable> { PngService.load(source) }

            wrapper.cleaned shouldBe true
            wrapper.onCollectionObserved()
            reports shouldBe emptyList()
        }
    })
