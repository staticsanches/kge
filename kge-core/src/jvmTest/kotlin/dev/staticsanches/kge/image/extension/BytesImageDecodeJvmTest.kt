package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asEngineBuffer
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyPngBytes
import dev.staticsanches.kge.image.tinyPngPixels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * JVM-only decode pin: the `java.nio.ByteBuffer` position must not influence
 * [BytesDecoder]; decode reads from the payload start, matching the
 * position-less web buffer.
 */
class BytesImageDecodeJvmTest :
    FunSpec({
        test("decode reads the payload from the start, ignoring the buffer position") {
            tinyPngBytes.asEngineBuffer().use { wrapper ->
                wrapper.resource.position(4)

                ImageService.load(wrapper, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
            }
        }
    })
