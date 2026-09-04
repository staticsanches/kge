package dev.staticsanches.kge.buffer

import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The allocation service contract under the extension mechanism: the facade
 * resolves the platform default, an override is observable by every caller, a
 * decorator reaches the engine default through [MemoryAllocatorService.original]
 * and `resetAll` restores it. The mechanism itself is proven by the T2
 * extension-contract test; this suite proves the real service on top of it.
 */
class MemoryAllocatorServiceTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        test("the facade allocates through the platform default") {
            MemoryAllocatorService.allocate(16).use { wrapper ->
                wrapper.resource.capacity() shouldBe 16
            }
        }

        test("negative sizes are rejected") {
            shouldThrow<IllegalArgumentException> { MemoryAllocatorService.allocate(-1) }
        }

        test("an overridden allocator is observable by every caller") {
            var allocated = 0
            MemoryAllocatorService.override(
                object : MemoryAllocatorService {
                    override fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer> {
                        allocated += sizeInBytes
                        return MemoryAllocatorService.original.allocate(sizeInBytes)
                    }
                },
            )

            MemoryAllocatorService.allocate(64).use { wrapper ->
                wrapper.resource.capacity() shouldBe 64
            }

            allocated shouldBe 64
        }

        test("resetAll restores the platform default") {
            MemoryAllocatorService.override(
                object : MemoryAllocatorService {
                    override fun allocate(sizeInBytes: Int): ResourceWrapper<ByteBuffer> = error("overridden allocator")
                },
            )

            shouldThrow<IllegalStateException> { MemoryAllocatorService.allocate(8) }
            KGEOverridable.Proxy.resetAll()

            MemoryAllocatorService.allocate(8).use { wrapper ->
                wrapper.resource.capacity() shouldBe 8
            }
        }
    })
