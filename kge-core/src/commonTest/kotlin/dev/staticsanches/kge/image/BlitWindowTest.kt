package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Rasterizer
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * `blitRegion` and raster through a window: the region is validated against
 * the source, the raw Sprite-to-Sprite path must not diverge from the
 * per-pixel oracle (a non-raw [PixmapDouble]), flips apply inside the region,
 * `blit(window)` equals `blitRegion`, and the raster primitives clip to a
 * window's local space.
 */
class BlitWindowTest :
    FunSpec({
        fun pixelAt(
            x: Int,
            y: Int,
        ) = Pixel.rgba(x * 16 + y + 1, 0x9A, 0x55, 0xFF)

        fun pattern(
            width: Int,
            height: Int,
        ): Sprite {
            val s = SpriteService.create(width, height, Pixmap.SampleMode.NORMAL, null)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    s.set(x, y, pixelAt(x, y))
                }
            }
            return s
        }

        fun patternDouble(
            width: Int,
            height: Int,
        ): Pixmap.Mutable {
            val p = PixmapDouble(width, height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    p.uncheckedSet(x, y, pixelAt(x, y))
                }
            }
            return p
        }

        fun blank(
            width: Int,
            height: Int,
        ): Sprite =
            SpriteService
                .create(width, height, Pixmap.SampleMode.NORMAL, null)
                .applyClosingIfFailed { clear(Colors.TRANSPARENT) }

        fun region(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
            origin: Int2D,
            size: Int2D,
            scale: Int = 1,
            flip: Pixmap.Flip = Pixmap.Flip.NONE,
            mode: Pixel.Mode = Pixel.Mode.Normal,
        ) = Rasterizer.blitRegion(target, x, y, source, origin, size, scale, flip, mode)

        fun blit(
            target: Pixmap.Mutable,
            x: Int,
            y: Int,
            source: Pixmap,
        ) = Rasterizer.blit(target, x, y, source, 1, Pixmap.Flip.NONE, Pixel.Mode.Normal)

        fun assertSameGrid(
            a: Pixmap,
            b: Pixmap,
            width: Int,
            height: Int,
        ) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    a.get(x, y) shouldBe b.get(x, y)
                }
            }
        }

        test("blitRegion paints the source region at the destination") {
            pattern(5, 5).use { s ->
                blank(6, 6).use { t ->
                    region(t, 2, 1, s, Int2D(1, 1), Int2D(2, 2))

                    t.get(2, 1) shouldBe s.get(1, 1)
                    t.get(3, 1) shouldBe s.get(2, 1)
                    t.get(2, 2) shouldBe s.get(1, 2)
                    t.get(3, 2) shouldBe s.get(2, 2)
                    t.get(1, 1) shouldBe Colors.TRANSPARENT
                    t.get(4, 1) shouldBe Colors.TRANSPARENT
                }
            }
        }

        test("blitRegion validates the region against the source") {
            pattern(5, 5).use { s ->
                blank(3, 3).use { t ->
                    shouldThrow<IllegalArgumentException> {
                        region(t, 0, 0, s, Int2D(4, 0), Int2D(2, 2))
                    }
                    shouldThrow<IllegalArgumentException> {
                        region(t, 0, 0, s, Int2D(0, 0), Int2D(0, 2))
                    }
                    shouldThrow<IllegalArgumentException> {
                        region(t, 0, 0, s, Int2D(-1, 0), Int2D(2, 2))
                    }
                }
            }
        }

        test("blitRegion on a non-raw Pixmap source matches the raw Sprite source") {
            pattern(5, 5).use { s ->
                val double = patternDouble(5, 5)
                blank(6, 6).use { viaSprite ->
                    blank(6, 6).use { viaDouble ->
                        region(viaSprite, 1, 1, s, Int2D(1, 1), Int2D(3, 3))
                        region(viaDouble, 1, 1, double, Int2D(1, 1), Int2D(3, 3))
                        assertSameGrid(viaSprite, viaDouble, 6, 6)
                    }
                }
            }
        }

        test("the raw Sprite-to-Sprite region path matches the per-pixel oracle across flips") {
            pattern(5, 5).use { s ->
                val double = patternDouble(5, 5)
                val regions = listOf(Int2D(1, 1) to Int2D(3, 3), Int2D(0, 0) to Int2D(2, 4))
                val flips =
                    listOf(Pixmap.Flip.NONE, Pixmap.Flip.HORIZONTAL, Pixmap.Flip.VERTICAL, Pixmap.Flip.BOTH)

                for ((origin, size) in regions) {
                    for (flip in flips) {
                        blank(6, 6).use { rawTarget ->
                            blank(6, 6).use { oracleTarget ->
                                region(rawTarget, 1, 1, s, origin, size, flip = flip)
                                region(oracleTarget, 1, 1, double, origin, size, flip = flip)
                                assertSameGrid(rawTarget, oracleTarget, 6, 6)
                            }
                        }
                    }
                }
            }
        }

        test("a scaled region paints a scale x scale block per source pixel") {
            pattern(5, 5).use { s ->
                blank(8, 8).use { t ->
                    region(t, 1, 1, s, Int2D(1, 1), Int2D(2, 2), scale = 2)

                    for (j in 0 until 2) {
                        for (i in 0 until 2) {
                            for (blockY in 0 until 2) {
                                for (blockX in 0 until 2) {
                                    t.get(1 + i * 2 + blockX, 1 + j * 2 + blockY) shouldBe s.get(1 + i, 1 + j)
                                }
                            }
                        }
                    }
                    t.get(5, 5) shouldBe Colors.TRANSPARENT
                }
            }
        }

        test("a region footprint past the target clips and paints nothing fully outside") {
            pattern(5, 5).use { s ->
                blank(3, 3).use { t ->
                    region(t, -1, -1, s, Int2D(0, 0), Int2D(3, 3))

                    t.get(0, 0) shouldBe s.get(1, 1)
                    t.get(1, 0) shouldBe s.get(2, 1)
                    t.get(0, 1) shouldBe s.get(1, 2)
                    t.get(1, 1) shouldBe s.get(2, 2)
                    t.get(2, 2) shouldBe Colors.TRANSPARENT

                    region(t, 5, 0, s, Int2D(1, 1), Int2D(2, 2))
                    t.get(0, 0) shouldBe s.get(1, 1)
                }
            }
        }

        test("blit of a window equals blitRegion of the same region") {
            pattern(5, 5).use { s ->
                blank(6, 6).use { viaWindow ->
                    blank(6, 6).use { viaRegion ->
                        blit(viaWindow, 1, 1, s.window(Int2D(1, 1), Int2D(2, 2)))
                        region(viaRegion, 1, 1, s, Int2D(1, 1), Int2D(2, 2))
                        assertSameGrid(viaWindow, viaRegion, 6, 6)
                    }
                }
            }
        }

        test("a whole-row blit copies the block in one call, a partial-row blit per row") {
            var copies = 0
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> = BufferService.original.allocate(sizeInBytes, name)

                    override fun copyInts(
                        dst: ByteBuffer,
                        dstFromByteOffset: Int,
                        source: ByteBuffer,
                        sourceFromByteOffset: Int,
                        count: Int,
                    ) {
                        copies++
                        BufferService.original.copyInts(dst, dstFromByteOffset, source, sourceFromByteOffset, count)
                    }
                },
            )

            // equal-width full surfaces: x == sx == 0 and w == both strides, so
            // the whole 4x4 block is one contiguous run per side -> one copy
            pattern(4, 4).use { s ->
                blank(4, 4).use { t ->
                    copies = 0
                    blit(t, 0, 0, s)
                    copies shouldBe 1

                    for (y in 0 until 4) {
                        for (x in 0 until 4) {
                            t.get(x, y) shouldBe s.get(x, y)
                        }
                    }
                }
            }

            // the same-width sprite placed at x == 1 leaves target rows partial
            // -> one copy per row
            pattern(3, 3).use { s ->
                blank(6, 6).use { t ->
                    copies = 0
                    blit(t, 1, 1, s)
                    copies shouldBe 3

                    for (y in 0 until 3) {
                        for (x in 0 until 3) {
                            t.get(1 + x, 1 + y) shouldBe s.get(x, y)
                        }
                    }
                    t.get(0, 1) shouldBe Colors.TRANSPARENT
                    t.get(4, 1) shouldBe Colors.TRANSPARENT
                }
            }

            // a sub-row region on equal-width surfaces is not contiguous either
            pattern(4, 4).use { s ->
                blank(4, 4).use { t ->
                    copies = 0
                    region(t, 0, 0, s, Int2D(0, 0), Int2D(2, 4))
                    copies shouldBe 4
                }
            }
        }

        test("fillRect and drawLine through a window stay inside the local space") {
            pattern(8, 8).use { s ->
                val w = s.window(Int2D(2, 2), Int2D(4, 4))

                Rasterizer.fillRect(w, 0, 0, 3, 3, Colors.RED, Pixel.Mode.Normal)
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        val expected = if (x in 2..5 && y in 2..5) Colors.RED else pixelAt(x, y)
                        s.get(x, y) shouldBe expected
                    }
                }
            }

            pattern(8, 8).use { s ->
                val w = s.window(Int2D(2, 2), Int2D(4, 4))

                Rasterizer.drawLine(w, 0, 0, 3, 3, Colors.RED, Pixel.Mode.Normal)
                for (i in 0..3) {
                    s.get(2 + i, 2 + i) shouldBe Colors.RED
                }
                s.get(3, 2) shouldBe pixelAt(3, 2)
            }
        }

        test("a blit through a window clips at the window edge and never writes outside") {
            pattern(8, 8).use { s ->
                val w = s.window(Int2D(2, 2), Int2D(4, 4))

                blank(2, 2).use { src ->
                    src.clear(Colors.GREEN)
                    Rasterizer.blit(w, 3, 2, src, 1, Pixmap.Flip.NONE, Pixel.Mode.Normal)
                }

                s.get(5, 4) shouldBe Colors.GREEN
                s.get(5, 5) shouldBe Colors.GREEN
                s.get(6, 4) shouldBe pixelAt(6, 4)
                s.get(6, 5) shouldBe pixelAt(6, 5)
                s.get(1, 1) shouldBe pixelAt(1, 1)
            }
        }
    })
