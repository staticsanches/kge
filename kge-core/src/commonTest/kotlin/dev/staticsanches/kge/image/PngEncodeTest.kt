package dev.staticsanches.kge.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Encode: a sprite round-trips through the codec — the decode oracle is
 * independent of the offline fixture. The returned wrapper is a caller-owned
 * [ByteBuffer] allocation with the engine lifecycle (close idempotent, use
 * after close fails fast).
 */
class PngEncodeTest :
    FunSpec({
        test("encode -> decode round-trips dimensions and pixels") {
            val sprite = distinctSprite()
            try {
                val encoded = PngService.encode(sprite)
                try {
                    PngService.decode(encoded.resource).use { back ->
                        back.width shouldBe sprite.width
                        back.height shouldBe sprite.height
                        back.rowMajorPixels() shouldBe sprite.rowMajorPixels()
                    }
                } finally {
                    encoded.close()
                }
            } finally {
                sprite.close()
            }
        }

        test("the encode wrapper closes idempotently and fails fast on reuse") {
            distinctSprite().use { sprite ->
                val encoded = PngService.encode(sprite)
                encoded.close()

                shouldThrow<IllegalStateException> { encoded.resource }

                encoded.close()
                encoded.cleaned shouldBe true
            }
        }
    })
