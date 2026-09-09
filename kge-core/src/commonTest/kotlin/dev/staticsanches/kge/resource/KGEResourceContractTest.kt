package dev.staticsanches.kge.resource

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KGEResourceContractTest :
    FunSpec({
        test("KGEResource is an AutoCloseable marker") {
            val resource =
                object : KGEResource {
                    override fun close() = Unit
                }

            val autoCloseable: AutoCloseable = resource
            autoCloseable.close()
        }

        test("a KGECleanAction invokes its payload once per call") {
            var calls = 0
            val action = KGECleanAction { calls++ }

            action()
            action()

            calls shouldBe 2
        }

        test("applyClosingIfFailed returns the receiver when the block succeeds, without closing") {
            val tracked = TrackingResource()
            val result = tracked.applyClosingIfFailed { this }

            result shouldBe tracked
            tracked.closed shouldBe false
        }

        test("applyClosingIfFailed closes the receiver when the block fails, rethrowing") {
            val tracked = TrackingResource()
            val failure = RuntimeException("init failed")

            val thrown =
                shouldThrow<RuntimeException> {
                    tracked.applyClosingIfFailed {
                        state = "written"
                        throw failure
                    }
                }

            thrown shouldBe failure
            tracked.closed shouldBe true
        }
    })

/** A [KGEResource] whose close is observable. */
private class TrackingResource : KGEResource {
    var state: String? = null
    var closed: Boolean = false

    override fun close() {
        closed = true
    }
}
