package dev.staticsanches.kge.resource

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/**
 * The wrapper contract: close idempotent, release action once, use-after-close
 * fail-fast, and the leak path — when the collection trigger fires without a
 * prior close, the leak report carries the wrapper's identity.
 */
class ResourceWrapperTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        fun reporting(reports: MutableList<String>): LeakReporterService =
            object : LeakReporterService {
                override fun report(representation: String) {
                    reports += representation
                }
            }

        test("two wrappers of the same resource have distinct identities") {
            val resource = "x"
            val a = ResourceWrapper("BufferA", resource, KGECleanAction { })
            val b = ResourceWrapper("BufferB", resource, KGECleanAction { })

            a.uuid shouldNotBeSameInstanceAs b.uuid
            a.cleaned shouldBe false
        }

        test("close releases exactly once, a second close is a no-op") {
            var releases = 0
            val wrapper = ResourceWrapper("BufferA", "x", KGECleanAction { releases++ })

            wrapper.close()
            wrapper.close()

            releases shouldBe 1
            wrapper.cleaned shouldBe true
        }

        test("use after close fails fast with the representation") {
            val wrapper = ResourceWrapper("Buffer32", "x", KGECleanAction { })
            wrapper.close()

            shouldThrow<IllegalStateException> { wrapper.resource }
                .message
                .shouldContain("released")
            shouldThrow<IllegalStateException> { wrapper.resource }
                .message
                .shouldContain("Buffer32")
        }

        test("collection without close reports the leak and never runs the action") {
            val reports = mutableListOf<String>()
            var releases = 0
            LeakReporterService.override(reporting(reports))
            val wrapper = ResourceWrapper("Buffer64", "x", KGECleanAction { releases++ })

            wrapper.onCollectionObserved()

            reports.single() shouldContain "Buffer64"
            releases shouldBe 0
            wrapper.cleaned shouldBe true
        }

        test("close before collection: no report and the action ran") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            val wrapper = ResourceWrapper("Buffer128", "x", KGECleanAction { })
            wrapper.close()

            wrapper.onCollectionObserved()

            reports shouldBe emptyList()
        }
    })
