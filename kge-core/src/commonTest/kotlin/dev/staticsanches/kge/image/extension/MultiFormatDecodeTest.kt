package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asEngineBuffer
import dev.staticsanches.kge.image.rowMajorPixels
import dev.staticsanches.kge.image.tinyBmpBytes
import dev.staticsanches.kge.image.tinyGifBytes
import dev.staticsanches.kge.image.tinyJpegBytes
import dev.staticsanches.kge.image.tinyPngPixels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * `BytesDecoder`'s multi-format breadth: the backend auto-detects the input
 * (`stbi_load_from_memory` on the JVM, `createImageBitmap` on the web), so one
 * decoder reads every format each platform supports. JPEG (lossy) and GIF
 * (palette-quantized) pin dimensions only; the 32-bit `BI_BITFIELDS` BMP is
 * lossless, so its pixels are pinned against the shared palette.
 */
class MultiFormatDecodeTest :
    FunSpec({
        test("load decodes the JPEG fixture to a 2x2 surface") {
            tinyJpegBytes.asEngineBuffer().use { source ->
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                }
            }
        }

        test("load decodes the BMP fixture losslessly to the shared palette") {
            tinyBmpBytes.asEngineBuffer().use { source ->
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                    sprite.rowMajorPixels() shouldBe tinyPngPixels
                }
            }
        }

        test("load decodes the GIF fixture to a 2x2 surface") {
            tinyGifBytes.asEngineBuffer().use { source ->
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                }
            }
        }
    })
