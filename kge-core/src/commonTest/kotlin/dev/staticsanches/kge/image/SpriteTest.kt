package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.byteAt
import dev.staticsanches.kge.resource.LeakReporterService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The concrete surface over native memory: storage is the byte layout the
 * engine uploads later — `(y * width + x) * 4`, RGBA little-endian — the
 * hot paths fill/copy the buffer directly, and ownership follows the
 * resource contract (fail-fast after close, idempotent close, leak reported
 * on collection). The Pixmap algorithms themselves are proven in PixmapTest.
 */
class SpriteTest :
    FunSpec({
        fun sprite(
            width: Int,
            height: Int,
            mode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
        ): Sprite =
            Sprite(
                width,
                height,
                BufferService.allocate(width * height * Int.SIZE_BYTES),
                mode,
            )

        test("a sprite exposes its dimensions and the given sample mode") {
            sprite(3, 2, Pixmap.SampleMode.PERIODIC).use { s ->
                s.width shouldBe 3
                s.height shouldBe 2
                s.sampleMode shouldBe Pixmap.SampleMode.PERIODIC
            }
        }

        test("zero dimensions throw") {
            val wide = BufferService.allocate(8)
            try {
                shouldThrow<IllegalArgumentException> {
                    Sprite(0, 1, wide, Pixmap.SampleMode.NORMAL)
                }
            } finally {
                wide.close()
            }
            val tall = BufferService.allocate(8)
            try {
                shouldThrow<IllegalArgumentException> {
                    Sprite(1, 0, tall, Pixmap.SampleMode.NORMAL)
                }
            } finally {
                tall.close()
            }
        }

        test("a buffer that cannot hold the surface throws") {
            val buffer = BufferService.allocate(12)
            try {
                shouldThrow<IllegalArgumentException> {
                    Sprite(2, 2, buffer, Pixmap.SampleMode.NORMAL)
                }
            } finally {
                buffer.close()
            }
        }

        test("RGBA pixels round trip") {
            sprite(2, 2).use { s ->
                val pixel = Pixel.rgba(10, 20, 30, 40)
                s.set(1, 1, pixel) shouldBe true
                s.get(1, 1) shouldBe pixel
            }
        }

        test("unchecked access round trips in bounds") {
            sprite(2, 2).use { s ->
                val pixel = Pixel.rgba(254, 253, 252, 251)
                s.uncheckedSet(0, 1, pixel)
                s.uncheckedGet(0, 1) shouldBe pixel
            }
        }

        test("native storage is RGBA little-endian at (y * width + x) * 4 bytes") {
            sprite(2, 2).use { s ->
                val buffer = s.buffer
                s.set(0, 0, Pixel.rgba(1, 2, 3, 4))
                buffer.byteAt(0) shouldBe 1
                buffer.byteAt(1) shouldBe 2
                buffer.byteAt(2) shouldBe 3
                buffer.byteAt(3) shouldBe 4

                s.set(1, 0, Pixel.rgba(5, 6, 7, 8))
                buffer.byteAt(4) shouldBe 5
                buffer.byteAt(7) shouldBe 8

                s.set(0, 1, Pixel.rgba(9, 10, 11, 12))
                buffer.byteAt(8) shouldBe 9
                buffer.byteAt(11) shouldBe 12
            }
        }

        test("clear fills every pixel through the buffer") {
            sprite(3, 2).use { s ->
                s.clear(Colors.RED)
                for (y in 0 until 2) {
                    for (x in 0 until 3) {
                        s.uncheckedGet(x, y) shouldBe Colors.RED
                    }
                }
            }
        }

        test("the raw buffer is the live storage and fails fast after close") {
            sprite(1, 1).use { s ->
                s.buffer.putInt(0, Colors.RED.nativeRGBA)
                s.get(0, 0) shouldBe Colors.RED

                s.close()
                shouldThrow<IllegalStateException> { s.buffer }
            }
        }

        test("close fails fast on read, write and sample, idempotently") {
            sprite(2, 2).use { s ->
                s.set(0, 0, Colors.RED)
                s.close()

                shouldThrow<IllegalStateException> { s.get(0, 0) }
                shouldThrow<IllegalStateException> { s.set(0, 0, Colors.RED) }
                shouldThrow<IllegalStateException> { s.sample(0.5f, 0.5f) }
                // the out-of-bounds paths never reach the storage — fail fast anyway
                shouldThrow<IllegalStateException> { s.get(-1, 0) }
                shouldThrow<IllegalStateException> { s.set(-1, 0, Colors.RED) }

                s.close()
            }
        }

        test("an unclosed sprite is reported on collection, a closed one is not") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )

            val unclosed = SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, "leaky")
            unclosed.onCollectionObserved()

            reports.single() shouldContain "byte buffer"
            reports.single() shouldContain "16 B"
            reports.single() shouldContain "leaky"

            val closed = sprite(1, 1)
            closed.close()
            closed.onCollectionObserved()

            reports.size shouldBe 1
        }

        test("toString carries the name when present") {
            SpriteService.create(2, 2, Pixmap.SampleMode.PERIODIC, "xmas").use { s ->
                s.toString() shouldBe "Sprite(2x2, PERIODIC, \"xmas\")"
            }
            sprite(1, 1).use { s ->
                s.toString() shouldBe "Sprite(1x1, NORMAL)"
            }
        }
    })
