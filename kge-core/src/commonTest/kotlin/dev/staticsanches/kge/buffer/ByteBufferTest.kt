package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.LeakReporterService
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.onCollectionObserved
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/**
 * The byte buffer contract: allocation through [MemoryAllocatorService] returns
 * a [ResourceWrapper] whose resource is a [ByteBuffer] of the requested size,
 * with the resource lifecycle of the engine (close idempotent, use-after-close
 * fail-fast, leak detection on collection). The initial content is unspecified
 * by contract, so every test sets the data it reads.
 */
class ByteBufferTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer> = MemoryAllocatorService.allocate(sizeInBytes)

        test("allocation returns a buffer of the requested size") {
            allocate(1024).use { wrapper ->
                wrapper.resource.capacity() shouldBe 1024
            }
        }

        test("allocation of zero bytes is allowed") {
            allocate(0).use { wrapper ->
                wrapper.resource.capacity() shouldBe 0
            }
        }

        test("two buffers have distinct identities") {
            val a = allocate(4)
            val b = allocate(4)

            a.uuid shouldNotBeSameInstanceAs b.uuid
            a.cleaned shouldBe false
            a.close()
            b.close()
        }

        test("close releases, a later access fails fast and a second close is a no-op") {
            val wrapper = allocate(8)
            wrapper.close()

            shouldThrow<IllegalStateException> { wrapper.resource }
                .message
                .shouldContain("released")
            wrapper.close()
            wrapper.cleaned shouldBe true
        }

        test("collection without close reports the leak") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )
            val wrapper = allocate(64)

            wrapper.onCollectionObserved()

            reports.single() shouldContain "byte buffer"
            reports.single() shouldContain "64"
            wrapper.cleaned shouldBe true
        }

        test("putByte writes the byte and byteAt reads it back") {
            allocate(8).use { wrapper ->
                val buffer = wrapper.resource

                buffer.putByte(1, 255)
                buffer.putByte(3, 7)

                buffer.byteAt(1) shouldBe 255
                buffer.byteAt(3) shouldBe 7
            }
        }

        test("putByte rejects values outside 0..255") {
            allocate(8).use { wrapper ->
                val buffer = wrapper.resource

                shouldThrow<IllegalArgumentException> { buffer.putByte(0, -1) }
                shouldThrow<IllegalArgumentException> { buffer.putByte(0, 256) }
            }
        }

        test("byte access outside the buffer throws") {
            allocate(8).use { wrapper ->
                val buffer = wrapper.resource

                shouldThrow<IndexOutOfBoundsException> { buffer.byteAt(-1) }
                shouldThrow<IndexOutOfBoundsException> { buffer.byteAt(8) }
                shouldThrow<IndexOutOfBoundsException> { buffer.putByte(8, 1) }
            }
        }

        test("putInt and getInt roundtrip") {
            allocate(16).use { wrapper ->
                val buffer = wrapper.resource

                buffer.putInt(4, 0x11223344)

                buffer.getInt(4) shouldBe 0x11223344
            }
        }

        test("ints follow the fixed little-endian byte order") {
            allocate(8).use { wrapper ->
                val buffer = wrapper.resource

                buffer.putInt(0, 0x11223344)

                buffer.byteAt(0) shouldBe 0x44
                buffer.byteAt(1) shouldBe 0x33
                buffer.byteAt(2) shouldBe 0x22
                buffer.byteAt(3) shouldBe 0x11
            }
        }

        test("getInt reads the little-endian order from individual bytes") {
            allocate(8).use { wrapper ->
                val buffer = wrapper.resource

                buffer.putByte(0, 0x44)
                buffer.putByte(1, 0x33)
                buffer.putByte(2, 0x22)
                buffer.putByte(3, 0x11)

                buffer.getInt(0) shouldBe 0x11223344
            }
        }

        test("int access outside the buffer throws") {
            allocate(8).use { wrapper ->
                val buffer = wrapper.resource

                shouldThrow<IndexOutOfBoundsException> { buffer.getInt(-1) }
                shouldThrow<IndexOutOfBoundsException> { buffer.getInt(5) }
                shouldThrow<IndexOutOfBoundsException> { buffer.putInt(5, 1) }
            }
        }

        test("fillInts fills the addressed int slots and leaves the rest") {
            allocate(16).use { wrapper ->
                val buffer = wrapper.resource

                buffer.putInt(0, 0x11111111)
                buffer.putInt(4, 0x55555555)
                buffer.putInt(8, 0x66666666)
                buffer.putInt(12, 0x44444444)
                buffer.fillInts(4, 2, 0x12345678)

                buffer.getInt(0) shouldBe 0x11111111
                buffer.getInt(4) shouldBe 0x12345678
                buffer.getInt(8) shouldBe 0x12345678
                buffer.getInt(12) shouldBe 0x44444444
            }
        }

        test("fillInts with a zero count is a no-op") {
            allocate(16).use { wrapper ->
                val buffer = wrapper.resource

                buffer.putInt(12, 0x11223344)
                buffer.fillInts(16, 0, 0x77665544)

                buffer.getInt(12) shouldBe 0x11223344
            }
        }

        test("fillInts outside the buffer throws") {
            allocate(16).use { wrapper ->
                val buffer = wrapper.resource

                shouldThrow<IndexOutOfBoundsException> { buffer.fillInts(-4, 1, 0) }
                shouldThrow<IndexOutOfBoundsException> { buffer.fillInts(12, 2, 0) }
                shouldThrow<IndexOutOfBoundsException> { buffer.fillInts(0, -1, 0) }
            }
        }

        test("copyInts copies count ints between distinct buffers") {
            allocate(16).use { sourceWrapper ->
                allocate(16).use { targetWrapper ->
                    val source = sourceWrapper.resource
                    val target = targetWrapper.resource

                    source.putInt(0, 1)
                    source.putInt(4, 2)
                    target.copyInts(4, source, 0, 2)

                    target.getInt(4) shouldBe 1
                    target.getInt(8) shouldBe 2
                }
            }
        }

        test("copyInts overlaps are memmove-safe in both directions") {
            allocate(32).use { wrapperBackward ->
                val buffer = wrapperBackward.resource
                for (i in 0 until 8) {
                    buffer.putInt(i * 4, i)
                }

                buffer.copyInts(4, buffer, 0, 4)

                for (i in 0 until 8) {
                    buffer.getInt(i * 4) shouldBe intArrayOf(0, 0, 1, 2, 3, 5, 6, 7)[i]
                }
            }
            allocate(32).use { wrapperForward ->
                val buffer = wrapperForward.resource
                for (i in 0 until 8) {
                    buffer.putInt(i * 4, i)
                }

                buffer.copyInts(0, buffer, 4, 4)

                for (i in 0 until 8) {
                    buffer.getInt(i * 4) shouldBe intArrayOf(1, 2, 3, 4, 4, 5, 6, 7)[i]
                }
            }
        }

        test("copyInts outside either buffer throws") {
            allocate(16).use { sourceWrapper ->
                allocate(16).use { targetWrapper ->
                    val source = sourceWrapper.resource
                    val target = targetWrapper.resource

                    shouldThrow<IndexOutOfBoundsException> { target.copyInts(12, source, 0, 2) }
                    shouldThrow<IndexOutOfBoundsException> { target.copyInts(0, source, -4, 1) }
                    shouldThrow<IndexOutOfBoundsException> { target.copyInts(0, source, 0, -1) }
                    shouldThrow<IndexOutOfBoundsException> { target.copyInts(0, source, 0, 5) }
                }
            }
        }
    })
