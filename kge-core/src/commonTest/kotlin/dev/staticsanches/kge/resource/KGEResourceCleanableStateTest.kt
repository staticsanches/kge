package dev.staticsanches.kge.resource

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The deterministic core of leak detection. The collection trigger is
 * platform-machinery (a `Cleaner` / FinalizationRegistry callback) whose timing
 * is not observable from a test; what must be proven here is the state machine
 * every trigger funnels into: cleaned flag, exactly-once action, exactly-once
 * report, and the close-after-leak ordering.
 */
class KGEResourceCleanableStateTest :
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

        test("clean runs the action exactly once and flips the flag") {
            var calls = 0
            val state = KGEResourceCleanableState("R1", KGECleanAction { calls++ })

            state.cleaned shouldBe false
            state.clean()
            state.clean()

            calls shouldBe 1
            state.cleaned shouldBe true
        }

        test("onCollected reports the representation once, never runs the action") {
            val reports = mutableListOf<String>()
            var calls = 0
            LeakReporterService.override(reporting(reports))
            val state = KGEResourceCleanableState("R2", KGECleanAction { calls++ })

            state.onCollected()
            state.onCollected()

            reports shouldBe listOf("R2")
            calls shouldBe 0
            state.cleaned shouldBe true
        }

        test("cleaning before collection suppresses the leak report") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            val state = KGEResourceCleanableState("R3", KGECleanAction { })

            state.clean()
            state.onCollected()

            reports shouldBe emptyList()
        }

        test("collection racing close: one side wins, the report loses if close won") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            var calls = 0
            val state = KGEResourceCleanableState("R4", KGECleanAction { calls++ })

            state.onCollected() // GC wins
            state.clean() // close comes too late

            reports shouldBe listOf("R4")
            calls shouldBe 0
        }

        test("close racing collection: the leak report loses if close won") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            var calls = 0
            val state = KGEResourceCleanableState("R5", KGECleanAction { calls++ })

            state.clean()
            state.onCollected()

            reports shouldBe emptyList()
            calls shouldBe 1
            state.cleaned shouldBe true
        }
    })
