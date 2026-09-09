package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The web source: [PngSource.fetch] over a data: URL
 * built from the fixture bytes loads through the facade. Node's fetch (undici)
 * serves data: URLs, so the js and wasmJs node suites exercise the same path.
 */
class PngFetchSourceTest :
    FunSpec({
        test("a fetch source loads the fixture PNG from a data: URL") {
            val dataUrl = "data:image/png;base64,$tinyPngBase64"

            PngService.load(PngSource.fetch(dataUrl)).use { sprite ->
                sprite.width shouldBe 2
                sprite.height shouldBe 2
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }
        }
    })
