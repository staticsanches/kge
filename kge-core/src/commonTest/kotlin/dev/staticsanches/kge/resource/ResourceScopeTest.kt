package dev.staticsanches.kge.resource

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private class RecordingResource(
    private val name: String,
    private val closes: MutableList<String>,
) : KGEResource {
    override fun close() {
        closes += name
    }
}

private class FailingResource(
    private val failure: RuntimeException,
    private val name: String,
    private val closes: MutableList<String>,
) : KGEResource {
    override fun close() {
        closes += name
        throw failure
    }
}

private fun <T : KGEResource> resourceKey(): ResourceScope.Key<T> = object : ResourceScope.Key<T> {}

/**
 * The [ResourceScope] contract: typed register/get through an identity [ResourceScope.Key],
 * insertion-ordered LIFO close that keeps closing past a failure, and fail-fast
 * use after close.
 */
class ResourceScopeTest :
    FunSpec({
        test("register stores the resource and get returns the same instance") {
            val scope = ResourceScope()
            val resource = RecordingResource("A", mutableListOf())
            val key = resourceKey<RecordingResource>()

            scope.register(key, resource) shouldBe resource
            scope.get(key) shouldBe resource
        }

        test("registering an already-registered key fails fast") {
            val scope = ResourceScope()
            val key = resourceKey<RecordingResource>()
            scope.register(key, RecordingResource("A", mutableListOf()))

            shouldThrow<IllegalArgumentException> {
                scope.register(key, RecordingResource("B", mutableListOf()))
            }
        }

        test("get of an unregistered key fails fast") {
            val scope = ResourceScope()

            shouldThrow<IllegalStateException> {
                scope.get(resourceKey<RecordingResource>())
            }
        }

        test("close closes the resources in reverse registration order") {
            val closes = mutableListOf<String>()
            val scope = ResourceScope()
            scope.register(resourceKey(), RecordingResource("A", closes))
            scope.register(resourceKey(), RecordingResource("B", closes))
            scope.register(resourceKey(), RecordingResource("C", closes))

            scope.close()

            closes shouldBe listOf("C", "B", "A")
        }

        test("close keeps closing past a failure and suppresses the later ones") {
            val closes = mutableListOf<String>()
            val failureFirst = RuntimeException("first in close order")
            val failureSecond = RuntimeException("second in close order")
            val scope = ResourceScope()
            scope.register(resourceKey<KGEResource>(), RecordingResource("A", closes))
            scope.register(resourceKey<KGEResource>(), FailingResource(failureSecond, "B", closes))
            scope.register(resourceKey<KGEResource>(), RecordingResource("C", closes))
            scope.register(resourceKey<KGEResource>(), FailingResource(failureFirst, "D", closes))

            val thrown = shouldThrow<RuntimeException> { scope.close() }

            thrown shouldBe failureFirst
            thrown.suppressedExceptions shouldBe listOf(failureSecond)
            closes shouldBe listOf("D", "C", "B", "A")
        }

        test("close is idempotent") {
            val closes = mutableListOf<String>()
            val scope = ResourceScope()
            scope.register(resourceKey<RecordingResource>(), RecordingResource("A", closes))

            scope.close()
            scope.close()

            closes shouldBe listOf("A")
        }

        test("register after close fails fast") {
            val scope = ResourceScope()
            scope.close()

            shouldThrow<IllegalStateException> {
                scope.register(resourceKey<RecordingResource>(), RecordingResource("A", mutableListOf()))
            }
        }

        test("get after close fails fast") {
            val scope = ResourceScope()
            val key = resourceKey<RecordingResource>()
            scope.register(key, RecordingResource("A", mutableListOf()))
            scope.close()

            shouldThrow<IllegalStateException> { scope.get(key) }
        }
    })
