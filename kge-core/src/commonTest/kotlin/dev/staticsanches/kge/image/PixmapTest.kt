package dev.staticsanches.kge.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The Pixmap contract — the default algorithm bodies over the raw accessors,
 * proven once on a light test double ([PixmapDouble], IntArray storage): the
 * mode-aware get policies, nearest/bilinear sampling, bounds-checked set,
 * clear, inv and the row-major Sequence. The real storage platform (Sprite
 * over native memory) is proven in SpriteTest; these tests pin the math.
 */
class PixmapTest :
    FunSpec({
        fun pixelAt(
            x: Int,
            y: Int,
        ) = Pixel.rgba(x * 16 + y + 1, 0x9A, 0x55, 0xFF)

        fun pattern(
            width: Int,
            height: Int,
            mode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        ): PixmapDouble {
            val p = PixmapDouble(width, height, mode)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    p.uncheckedSet(x, y, pixelAt(x, y))
                }
            }
            return p
        }

        test("NORMAL get returns transparent outside the surface") {
            val p = pattern(2, 2)

            p.get(-1, 0) shouldBe Pixel.rgba(0, 0, 0, 0)
            p.get(2, 0) shouldBe Pixel.rgba(0, 0, 0, 0)
            p.get(0, -1) shouldBe Pixel.rgba(0, 0, 0, 0)
            p.get(0, 2) shouldBe Pixel.rgba(0, 0, 0, 0)
            p.get(4, 4) shouldBe Pixel.rgba(0, 0, 0, 0)
        }

        test("PERIODIC get wraps with abs over the dimensions") {
            val p = pattern(3, 2, Pixmap.SampleMode.PERIODIC)

            p.get(-1, 0) shouldBe pixelAt(1, 0) // abs(-1 % 3) = 1
            p.get(-2, 0) shouldBe pixelAt(2, 0) // abs(-2 % 3) = 2
            p.get(3, 0) shouldBe pixelAt(0, 0) // 3 % 3 = 0
            p.get(0, -1) shouldBe pixelAt(0, 1) // abs(-1 % 2) = 1
            p.get(0, 2) shouldBe pixelAt(0, 0) // 2 % 2 = 0
            p.get(-1, -1) shouldBe pixelAt(1, 1)
        }

        test("CLAMP get clamps to the surface edges") {
            val p = pattern(3, 2, Pixmap.SampleMode.CLAMP)

            p.get(-1, 0) shouldBe pixelAt(0, 0)
            p.get(5, 0) shouldBe pixelAt(2, 0)
            p.get(0, -5) shouldBe pixelAt(0, 0)
            p.get(0, 5) shouldBe pixelAt(0, 1)
            p.get(99, 99) shouldBe pixelAt(2, 1)
        }

        test("sample picks the nearest pixel and clamps beyond 1") {
            val p = pattern(2, 2)

            p.sample(0f, 0f) shouldBe pixelAt(0, 0)
            p.sample(1f, 1f) shouldBe pixelAt(1, 1)
            p.sample(0.999f, 0.999f) shouldBe pixelAt(1, 1)
            p.sample(0.25f, 0.25f) shouldBe pixelAt(0, 0) // truncation: int(0.5) = 0
            p.sample(0.75f, 0.75f) shouldBe pixelAt(1, 1) // truncation: int(1.5) = 1
            p.sample(1.5f, 0.5f) shouldBe pixelAt(1, 1) // u beyond 1 clamps to the last column
            p.sample(0.34f, 1.51f) shouldBe pixelAt(0, 1) // v beyond 1 clamps to the last row
        }

        test("PERIODIC sample wraps negative u, NORMAL does not") {
            pattern(4, 1, Pixmap.SampleMode.PERIODIC)
                .sample(-0.25f, 0.5f)
                .shouldBe(pixelAt(1, 0)) // int(-1) wraps to abs(-1 % 4) = 1
            pattern(4, 1, Pixmap.SampleMode.NORMAL)
                .sample(-0.25f, 0.5f)
                .shouldBe(Pixel.rgba(0, 0, 0, 0))
        }

        test("sampleBL blends the four neighbors, truncates and forces alpha") {
            val p = PixmapDouble(2, 2)
            p.uncheckedSet(0, 0, Colors.BLACK)
            p.uncheckedSet(1, 0, Colors.WHITE)
            p.uncheckedSet(0, 1, Colors.BLACK)
            p.uncheckedSet(1, 1, Colors.WHITE)

            val c = p.sampleBL(0.5f, 0.5f) // every neighbor at weight 1/4 → 127.5

            c.r shouldBe 127
            c.g shouldBe 127
            c.b shouldBe 127
            c.a shouldBe 255
        }

        test("set is bounds-checked: false and no write outside, true inside") {
            val p = PixmapDouble(2, 2)

            p.set(1, 1, Colors.BLUE) // sentinel the out-of-bounds writes must not touch
            p.set(-1, 0, Colors.RED) shouldBe false
            p.set(0, 2, Colors.RED) shouldBe false
            p.set(0, 0, Colors.RED) shouldBe true

            p.uncheckedGet(0, 0) shouldBe Colors.RED
            p.uncheckedGet(1, 1) shouldBe Colors.BLUE
        }

        test("clear fills every pixel") {
            val p = PixmapDouble(3, 2)

            p.clear(Colors.RED)

            for (y in 0 until 2) {
                for (x in 0 until 3) {
                    p.uncheckedGet(x, y) shouldBe Colors.RED
                }
            }
        }

        test("inv inverts the channels and keeps the alpha") {
            val p = PixmapDouble(2, 1)
            p.uncheckedSet(0, 0, Pixel.rgba(10, 200, 30, 40))
            p.uncheckedSet(1, 0, Pixel.rgba(255, 0, 0, 255))

            p.inv()

            p.uncheckedGet(0, 0) shouldBe Pixel.rgba(245, 55, 225, 40)
            p.uncheckedGet(1, 0) shouldBe Pixel.rgba(0, 255, 255, 255)
        }

        test("the sequence iterates row-major with distinct pixels") {
            val p = pattern(3, 2)

            p.toList() shouldBe
                (0 until 2).flatMap { y -> (0 until 3).map { x -> p.uncheckedGet(x, y) } }
            p.toList().distinct().size shouldBe 6
        }
    })

/** Minimal MutablePixmap over an IntArray — only the raw accessors are real. */
private class PixmapDouble(
    override val width: Int,
    override val height: Int,
    override var sampleMode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
) : MutablePixmap {
    private val pixels = IntArray(width * height)

    override fun uncheckedGet(
        x: Int,
        y: Int,
    ): Pixel = Pixel.fromNativeRGBA(pixels[y * width + x])

    override fun uncheckedSet(
        x: Int,
        y: Int,
        pixel: Pixel,
    ) {
        pixels[y * width + x] = pixel.nativeRGBA
    }
}
