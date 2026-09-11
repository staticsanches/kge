package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The raw-backed capability: a [Pixmap.RawBacked] maps a local pixel to an
 * int-element offset over its own buffer; a contiguous surface ([Sprite]) is
 * raw-backed, a non-contiguous one is not, and the buffer accessor fails fast
 * after close.
 */
@OptIn(KGESensitiveAPI::class)
class PixmapRawBackedTest :
    FunSpec({
        test("a Sprite exposes its own buffer, stride, base index and index mapping") {
            SpriteService.create(3, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                sprite.stride shouldBe 3
                sprite.baseIndex shouldBe 0
                sprite.buffer.capacity() shouldBe 3 * 2 * Int.SIZE_BYTES
                for (y in 0 until 2) {
                    for (x in 0 until 3) {
                        sprite.index(x, y) shouldBe (y * 3 + x)
                    }
                }
            }
        }

        test("a write through the buffer is visible through get") {
            SpriteService.create(3, 2, Pixmap.SampleMode.NORMAL, null).use { sprite ->
                sprite.buffer.putInt(0, Colors.RED.nativeRGBA)
                sprite.get(0, 0) shouldBe Colors.RED
            }
        }

        test("a non-contiguous Pixmap is not raw-backed") {
            val double: Pixmap = PixmapDouble(2, 2)
            (double is Pixmap.RawBacked) shouldBe false
        }

        test("buffer access fails fast after close") {
            val sprite = SpriteService.create(1, 1, Pixmap.SampleMode.NORMAL, null)
            sprite.close()
            shouldThrow<IllegalStateException> { sprite.buffer }
        }
    })
