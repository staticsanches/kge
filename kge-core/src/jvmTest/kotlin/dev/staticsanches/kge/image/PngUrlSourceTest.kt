package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The JVM source of the S5 load boundary: [PngSource.url] over the classpath
 * resource PNG (the same bytes as the commonTest fixture) loads through the
 * facade. The URL stream I/O runs on Dispatchers.IO inside the source.
 */
class PngUrlSourceTest :
    FunSpec({
        test("a URL source loads the classpath resource PNG") {
            val resource = checkNotNull(javaClass.getResource("/tiny.png"))

            PngService.load(PngSource.url(resource)).use { sprite ->
                sprite.width shouldBe 2
                sprite.height shouldBe 2
                sprite.rowMajorPixels() shouldBe tinyPngPixels
            }
        }
    })
