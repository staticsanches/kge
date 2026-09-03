package dev.staticsanches.kge.resource

import dev.staticsanches.kge.overridable.KGEOverridable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The extension-contract proof for the leak-report seam: leak reporting is an
 * engine capability with an engine-fixed default (log), replaceable by a
 * consumer for the whole process — a test overrides it to collect reports and
 * keep leaks observable without parsing logs.
 */
class LeakReporterServiceTest :
    FunSpec({
        afterTest {
            KGEOverridable.Proxy.resetAll()
        }

        test("an override receives the leak report") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )

            LeakReporterService.report("DummyBuffer (uuid: 01)")

            reports shouldBe listOf("DummyBuffer (uuid: 01)")
        }

        test("a decorator delegating to original wraps the engine default") {
            val original = LeakReporterService.original
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                        original.report(representation)
                    }
                },
            )

            LeakReporterService.report("DummyBuffer (uuid: 02)")

            reports shouldBe listOf("DummyBuffer (uuid: 02)")
        }

        test("a later override supersedes the previous one") {
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) = Unit
                },
            )
            val reports = mutableListOf<String>()
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) {
                        reports += representation
                    }
                },
            )

            LeakReporterService.report("DummyBuffer (uuid: 03)")

            reports shouldBe listOf("DummyBuffer (uuid: 03)")
        }

        test("resetAll restores the engine default") {
            LeakReporterService.override(
                object : LeakReporterService {
                    override fun report(representation: String) = Unit
                },
            )

            KGEOverridable.Proxy.resetAll()

            LeakReporterService.report("DummyBuffer (uuid: 04)")
        }
    })
