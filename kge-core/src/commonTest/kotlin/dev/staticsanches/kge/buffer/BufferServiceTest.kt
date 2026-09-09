package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The allocation service contract under the extension mechanism: the facade
 * resolves the platform default, an override is observable by every caller, a
 * decorator reaches the engine default through [BufferService.original]
 * and `resetAll` restores it.
 */
class BufferServiceTest :
    FunSpec({
        test("the facade allocates through the platform default") {
            BufferService.allocate(16).use { wrapper ->
                wrapper.resource.capacity() shouldBe 16
            }
        }

        test("negative sizes are rejected") {
            shouldThrow<IllegalArgumentException> { BufferService.allocate(-1) }
        }

        test("an overridden allocator is observable by every caller") {
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

            BufferService.allocate(64).use { wrapper ->
                wrapper.resource.capacity() shouldBe 64
            }

            allocated shouldBe 64
        }

        test("resetAll restores the platform default") {
            BufferService.override(
                object : BufferService {
                    override fun allocate(
                        sizeInBytes: Int,
                        name: String?,
                    ): ResourceWrapper<ByteBuffer> = error("overridden allocator")

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

            shouldThrow<IllegalStateException> { BufferService.allocate(8) }
            KGEOverridable.Proxy.resetAll()

            BufferService.allocate(8).use { wrapper ->
                wrapper.resource.capacity() shouldBe 8
            }
        }
    })
