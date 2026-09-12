package dev.staticsanches.kge.image.extension

import dev.staticsanches.kge.image.ImageService
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.asEngineBuffer
import dev.staticsanches.kge.image.tinyWebpBytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Browser-only decode breadth that the JVM backend cannot serve: STB has no
 * WEBP decoder, while the browser's `createImageBitmap` does.
 */
class MultiFormatDecodeBrowserTest :
    FunSpec({
        test("load decodes the WEBP fixture to a 2x2 surface") {
            tinyWebpBytes.asEngineBuffer().use { source ->
                ImageService.load(source, BytesDecoder, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                    sprite.width shouldBe 2
                    sprite.height shouldBe 2
                }
            }
        }
    })
