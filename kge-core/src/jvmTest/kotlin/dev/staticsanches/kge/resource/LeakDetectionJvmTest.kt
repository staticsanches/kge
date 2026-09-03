package dev.staticsanches.kge.resource

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Best-effort JVM wiring proof: a wrapper that goes out of scope unclosed is
 * eventually reported by the Cleaner. GC timing is not deterministic, so the
 * test polls for up to a generous window instead of relying on one pass.
 */
class LeakDetectionJvmTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        test("an unclosed wrapper is reported once collected") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )

            fun scope() {
                val wrapper = ResourceWrapper("ScopeBuffer", "x", KGECleanAction { })
                wrapper.hashCode() // ensure constructor ran
            }
            scope() // wrapper unreachable here

            val deadline = System.currentTimeMillis() + 15_000
            while (reports.isEmpty() && System.currentTimeMillis() < deadline) {
                System.gc()
                Thread.sleep(50)
            }

            reports.size shouldBe 1
            reports.single() shouldContain "ScopeBuffer"
        }
    })
