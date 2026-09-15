package dev.staticsanches.kge.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private class Closer(
    private val closes: MutableList<String>,
    private val name: String,
) : KGEResource {
    override fun close() {
        closes += name
    }
}

private class FailingCloser(
    private val failure: RuntimeException,
    private val closes: MutableList<String>,
    private val name: String,
) : KGEResource {
    override fun close() {
        closes += name
        throw failure
    }
}

/**
 * The composite holder: created with its first element, so it is never empty
 * until closed — emptiness is the closed state. Ordered typed access and
 * iteration for its owner, fail-fast use once empty, and idempotent close-all
 * in reverse add order.
 */
@OptIn(KGESensitiveAPI::class)
class CompositeResourceTest :
    FunSpec({
        test("a live composite holds its first element and appends in order") {
            val first = Closer(mutableListOf(), "A")
            val second = Closer(mutableListOf(), "B")
            val composite = CompositeResource(first)

            composite.size shouldBe 1
            composite[0] shouldBeSameInstanceAs first

            composite.add(second)

            composite.size shouldBe 2
            composite[1] shouldBeSameInstanceAs second
        }

        test("iteration visits the elements in order") {
            val first = Closer(mutableListOf(), "A")
            val second = Closer(mutableListOf(), "B")
            val composite = CompositeResource(first)
            composite.add(second)

            val visited = mutableListOf<Closer>()
            composite.forEach { visited += it }

            visited shouldBe listOf(first, second)
        }

        test("close releases every element in reverse add order, empties the composite and is idempotent") {
            val closes = mutableListOf<String>()
            val composite = CompositeResource(Closer(closes, "A"))
            composite.add(Closer(closes, "B"))

            composite.close()
            composite.close()

            closes shouldBe listOf("B", "A")
            composite.size shouldBe 0
        }

        test("get, iteration and add fail fast once the composite is closed") {
            val element = Closer(mutableListOf(), "A")
            val composite = CompositeResource(element)
            composite.close()

            shouldThrow<IllegalStateException> { composite[0] }
            shouldThrow<IllegalStateException> { composite.forEach {} }
            shouldThrow<IllegalStateException> { composite.add(element) }
        }

        test("close keeps closing past a failure, rethrowing the first and suppressing the later") {
            val closes = mutableListOf<String>()
            val firstFailure = RuntimeException("first in close order")
            val secondFailure = RuntimeException("second in close order")
            val composite = CompositeResource<KGEResource>(Closer(closes, "A"))
            composite.add(FailingCloser(secondFailure, closes, "B"))
            composite.add(Closer(closes, "C"))
            composite.add(FailingCloser(firstFailure, closes, "D"))

            val thrown = shouldThrow<RuntimeException> { composite.close() }

            thrown shouldBe firstFailure
            thrown.suppressedExceptions shouldBe listOf(secondFailure)
            closes shouldBe listOf("D", "C", "B", "A")
        }
    })
