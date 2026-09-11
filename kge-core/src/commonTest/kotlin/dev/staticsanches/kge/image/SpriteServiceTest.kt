package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyClosingIfFailed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The surface-creation service on the extension mechanism: the default is
 * platform-independent (it allocates through the current
 * [BufferService]), contract A proves an overridden allocator is the
 * one the service uses, contract B proves an overridden service decorator is
 * observable, and duplicate is a detached pixel copy preserving the sample
 * mode.
 */
class SpriteServiceTest :
    FunSpec({
        test("the facade creates through the platform-independent default") {
            SpriteService.create(2, 1, Pixmap.SampleMode.PERIODIC, "workspace").use { s ->
                s.width shouldBe 2
                s.height shouldBe 1
                s.sampleMode shouldBe Pixmap.SampleMode.PERIODIC
                s.buffer.capacity() shouldBe 8
            }
        }

        test("invalid dimensions fail before any allocation") {
            var allocated = 0
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> {
                        allocated += sizeInBytes
                        return BufferService.original.allocate(sizeInBytes, name)
                    }

                    override fun fillInts(
                        target: ByteBuffer,
                        fromByteOffset: Int,
                        count: Int,
                        value: Int,
                    ) {
                        BufferService.original.fillInts(target, fromByteOffset, count, value)
                    }

                    override fun copyInts(
                        dst: ByteBuffer,
                        dstFromByteOffset: Int,
                        source: ByteBuffer,
                        sourceFromByteOffset: Int,
                        count: Int,
                    ) {
                        BufferService.original.copyInts(dst, dstFromByteOffset, source, sourceFromByteOffset, count)
                    }
                },
            )

            shouldThrow<IllegalArgumentException> {
                SpriteService.create(0, 1, Pixmap.SampleMode.NORMAL, null)
            }
            shouldThrow<IllegalArgumentException> {
                SpriteService.create(1, 0, Pixmap.SampleMode.NORMAL, null)
            }

            allocated shouldBe 0
        }

        test("creation and duplication allocate through the current allocator (contract A)") {
            var allocated = 0
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> {
                        allocated += sizeInBytes
                        return BufferService.original.allocate(sizeInBytes, name)
                    }

                    override fun fillInts(
                        target: ByteBuffer,
                        fromByteOffset: Int,
                        count: Int,
                        value: Int,
                    ) {
                        BufferService.original.fillInts(target, fromByteOffset, count, value)
                    }

                    override fun copyInts(
                        dst: ByteBuffer,
                        dstFromByteOffset: Int,
                        source: ByteBuffer,
                        sourceFromByteOffset: Int,
                        count: Int,
                    ) {
                        BufferService.original.copyInts(dst, dstFromByteOffset, source, sourceFromByteOffset, count)
                    }
                },
            )

            SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { s ->
                s.buffer.capacity() shouldBe 16
            }
            allocated shouldBe 16

            // 16 (create 2x2) + 4 (create 1x1) + 4 (duplicate of 1x1)
            SpriteService.create(1, 1, Pixmap.SampleMode.NORMAL, null).use { src ->
                SpriteService.duplicate(src).use { dup ->
                    dup.buffer.capacity() shouldBe 4
                }
            }
            allocated shouldBe 24
        }

        test("a decorator creation service is observable (contract B)") {
            val original = SpriteService.original
            SpriteService.override(
                object : SpriteService {
                    override fun create(
                        width: Int,
                        height: Int,
                        sampleMode: Pixmap.SampleMode,
                        name: String?,
                    ): Sprite =
                        original
                            .create(width, height, sampleMode, name)
                            .applyClosingIfFailed { clear(Colors.BLACK) }

                    override fun duplicate(sprite: Sprite): Sprite = original.duplicate(sprite)
                },
            )

            SpriteService.create(2, 2, Pixmap.SampleMode.NORMAL, null).use { s ->
                for (y in 0 until 2) {
                    for (x in 0 until 2) {
                        s.uncheckedGet(x, y) shouldBe Colors.BLACK
                    }
                }
            }
        }

        test("duplicate copies pixels, keeps the sample mode and detaches storage") {
            val src = SpriteService.create(2, 2, Pixmap.SampleMode.PERIODIC, "src")
            try {
                src.set(0, 0, Colors.RED)
                src.set(1, 1, Colors.BLUE)

                val dup = SpriteService.duplicate(src)
                try {
                    dup.width shouldBe 2
                    dup.height shouldBe 2
                    dup.sampleMode shouldBe Pixmap.SampleMode.PERIODIC
                    dup.get(0, 0) shouldBe Colors.RED
                    dup.get(1, 1) shouldBe Colors.BLUE

                    dup.set(0, 0, Colors.GREEN)
                    dup.get(0, 0) shouldBe Colors.GREEN
                    src.get(0, 0) shouldBe Colors.RED

                    src.close()
                    dup.get(0, 0) shouldBe Colors.GREEN
                } finally {
                    dup.close()
                }
            } finally {
                src.close()
            }
        }
    })
