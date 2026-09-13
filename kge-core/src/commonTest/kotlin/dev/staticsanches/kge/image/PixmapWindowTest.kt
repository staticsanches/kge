package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The window view: a `Pixmap` over a sub-rectangle in its own local space,
 * reached through `Pixmap.window`. A read-only view reads through offset, the
 * writable view propagates writes, validation is strict, the raw backing
 * composes to the root, and a raster fill stays inside the window.
 */
@OptIn(KGESensitiveAPI::class)
class PixmapWindowTest :
    FunSpec({
        fun pixelAt(
            x: Int,
            y: Int,
        ) = Pixel.rgba(x * 16 + y + 1, 0x9A, 0x55, 0xFF)

        fun sprite(
            width: Int = 5,
            height: Int = 5,
            mode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        ): Sprite {
            val s = SpriteService.create(width, height, mode, null)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    s.set(x, y, pixelAt(x, y))
                }
            }
            return s
        }

        test("the view exposes the size, local bounds and contains") {
            sprite().use { s ->
                val w = s.window(Int2D(1, 1), Int2D(3, 3))

                w.width shouldBe 3
                w.height shouldBe 3
                w.lowerBoundInclusive shouldBe Int2D.ZERO
                w.upperBoundExclusive shouldBe Int2D(3, 3)
                w.contains(0, 0) shouldBe true
                w.contains(2, 2) shouldBe true
                w.contains(3, 3) shouldBe false
            }
        }

        test("a read-only view reads through the origin and stays local") {
            sprite().use { s ->
                val w: Pixmap = s.window(Int2D(1, 1), Int2D(3, 3))

                w.get(0, 0) shouldBe s.get(1, 1)
                w.get(2, 2) shouldBe s.get(3, 3)
                w.get(3, 0) shouldBe Colors.TRANSPARENT
                w.get(0, 3) shouldBe Colors.TRANSPARENT
            }
        }

        test("a non-mutable source reaches the read-only window branch and composes") {
            sprite().use { s ->
                val plain: Pixmap =
                    object : Pixmap {
                        override val width: Int get() = s.width
                        override val height: Int get() = s.height
                        override val sampleMode: Pixmap.SampleMode get() = s.sampleMode

                        override fun uncheckedGet(
                            x: Int,
                            y: Int,
                        ): Pixel = s.uncheckedGet(x, y)
                    }

                val inner = plain.window(Int2D(1, 1), Int2D(3, 3))
                (inner is Pixmap.Mutable) shouldBe false
                inner.get(0, 0) shouldBe s.get(1, 1)
                inner.get(3, 0) shouldBe Colors.TRANSPARENT

                // the read-only window must be built over the view, not the root
                val outer = inner.window(Int2D(1, 1), Int2D(2, 2))
                outer.get(0, 0) shouldBe s.get(2, 2)
            }
        }

        test("the read-only window seeds sampleMode from the source and does not write back") {
            sprite(mode = Pixmap.SampleMode.PERIODIC).use { s ->
                val plain: Pixmap =
                    object : Pixmap {
                        override val width: Int get() = s.width
                        override val height: Int get() = s.height
                        override val sampleMode: Pixmap.SampleMode get() = s.sampleMode

                        override fun uncheckedGet(
                            x: Int,
                            y: Int,
                        ): Pixel = s.uncheckedGet(x, y)
                    }

                val w = plain.window(Int2D(0, 0), Int2D(2, 2))
                w.sampleMode shouldBe Pixmap.SampleMode.PERIODIC
                s.sampleMode shouldBe Pixmap.SampleMode.PERIODIC
            }
        }

        test("a non-mutable raw source yields a read-only raw-backed window") {
            sprite().use { s ->
                val rawOnly: Pixmap.RawBacked =
                    object : Pixmap.RawBacked {
                        override val width: Int get() = s.width
                        override val height: Int get() = s.height
                        override val sampleMode: Pixmap.SampleMode get() = s.sampleMode
                        override val buffer: ByteBuffer get() = s.buffer
                        override val stride: Int get() = s.stride
                        override val baseIndex: Int get() = s.baseIndex

                        override fun uncheckedGet(
                            x: Int,
                            y: Int,
                        ): Pixel = s.uncheckedGet(x, y)
                    }

                val w = rawOnly.window(Int2D(1, 1), Int2D(3, 3)).shouldBeInstanceOf<Pixmap.RawBacked>()
                (w is Pixmap.Mutable) shouldBe false
                (w.buffer === s.buffer) shouldBe true
                w.baseIndex shouldBe s.index(1, 1)
                w.index(0, 0) shouldBe s.index(1, 1)

                // the window resolves the source buffer on demand: closing the
                // root surfaces the release fail-fast through the window
                s.close()
                shouldThrow<IllegalStateException> { w.buffer }
            }
        }

        test("a writable view propagates set inside and refuses out of bounds") {
            sprite().use { s ->
                val w: Pixmap.Mutable = s.window(Int2D(1, 1), Int2D(3, 3))

                w.set(1, 1, Colors.RED) shouldBe true
                s.get(2, 2) shouldBe Colors.RED
                w.set(0, 3, Colors.BLUE) shouldBe false
                s.get(1, 4) shouldBe pixelAt(1, 4)

                for (y in 0 until 3) {
                    for (x in 0 until 3) {
                        if (x == 1 && y == 1) continue
                        s.get(x + 1, y + 1) shouldBe pixelAt(x + 1, y + 1)
                    }
                }
            }
        }

        test("clear on a writable view clears only the source sub-rectangle") {
            sprite().use { s ->
                val w = s.window(Int2D(1, 1), Int2D(3, 3))

                w.clear(Colors.BLUE)

                for (y in 0 until 5) {
                    for (x in 0 until 5) {
                        val expected = if (x in 1..3 && y in 1..3) Colors.BLUE else pixelAt(x, y)
                        s.get(x, y) shouldBe expected
                    }
                }
            }
        }

        test("the sequence iterates the window pixels row-major in local order") {
            sprite().use { s ->
                val w = s.window(Int2D(1, 1), Int2D(3, 3))

                w.toList() shouldBe
                    (0 until 3).flatMap { y -> (0 until 3).map { x -> s.get(x + 1, y + 1) } }
            }
        }

        test("sampleMode is seeded from the source and independently mutable") {
            sprite(mode = Pixmap.SampleMode.NORMAL).use { s ->
                val w: Pixmap.Mutable = s.window(Int2D(0, 0), Int2D(2, 2))
                w.sampleMode shouldBe Pixmap.SampleMode.NORMAL

                w.sampleMode = Pixmap.SampleMode.PERIODIC
                w.sampleMode shouldBe Pixmap.SampleMode.PERIODIC
                s.sampleMode shouldBe Pixmap.SampleMode.NORMAL
            }
        }

        test("validation throws for an out-of-source region, zero/negative size and negative origin") {
            sprite().use { s ->
                shouldThrow<IllegalArgumentException> { s.window(Int2D(3, 0), Int2D(3, 1)) }
                shouldThrow<IllegalArgumentException> { s.window(Int2D(0, 0), Int2D(0, 2)) }
                shouldThrow<IllegalArgumentException> { s.window(Int2D(0, 0), Int2D(-1, 2)) }
                shouldThrow<IllegalArgumentException> { s.window(Int2D(-1, 0), Int2D(2, 2)) }
            }
        }

        test("a window over a Sprite is raw-backed with the source buffer at a shifted base") {
            sprite().use { s ->
                val window = s.window(Int2D(1, 1), Int2D(3, 3)).shouldBeInstanceOf<Pixmap.RawBacked>()

                (window.buffer === s.buffer) shouldBe true
                window.stride shouldBe s.stride
                window.baseIndex shouldBe s.index(1, 1)
                window.index(0, 0) shouldBe s.index(1, 1)
            }
        }

        test("a window of a window composes the offset to the root base") {
            sprite().use { s ->
                val inner = s.window(Int2D(1, 1), Int2D(3, 3))
                val outer = inner.window(Int2D(1, 1), Int2D(2, 2)).shouldBeInstanceOf<Pixmap.RawBacked>()

                outer.baseIndex shouldBe s.index(2, 2)
            }
        }

        test("a window over a non-contiguous Pixmap is not raw-backed") {
            val double: Pixmap.Mutable = PixmapDouble(5, 5)
            val w = double.window(Int2D(1, 1), Int2D(2, 2))

            (w is Pixmap.RawBacked) shouldBe false
        }

        test("fillRect through a window paints the source sub-rectangle and nothing outside") {
            sprite().use { s ->
                val w = s.window(Int2D(1, 1), Int2D(3, 3))

                Rasterizer.fillRect(w, 0, 0, 2, 2, Colors.RED, Pixel.Mode.Normal)

                for (y in 0 until 5) {
                    for (x in 0 until 5) {
                        val expected = if (x in 1..3 && y in 1..3) Colors.RED else pixelAt(x, y)
                        s.get(x, y) shouldBe expected
                    }
                }
            }
        }
    })
