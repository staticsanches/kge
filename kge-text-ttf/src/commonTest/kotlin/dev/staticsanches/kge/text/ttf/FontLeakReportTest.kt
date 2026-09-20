package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.resource.LeakReporterService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * The font's leak identity: an unclosed font is reported as the face it owns,
 * never as the payload it was built from; a closed font is not reported.
 */
@OptIn(KGESensitiveAPI::class)
class FontLeakReportTest :
    FunSpec({
        fun reporting(reports: MutableList<String>): LeakReporterService =
            object : LeakReporterService {
                override fun report(representation: String) {
                    reports += representation
                }
            }

        test("an unclosed font is reported as the face, not the payload") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            try {
                val font = Font.load(Roboto.variableFont)

                font.onCollectionObserved()

                reports.single() shouldContain "font face"
                reports.single() shouldNotContain "byte buffer"
            } finally {
                // kge-core resets overrides between its own tests only, so this
                // module restores the engine default itself.
                LeakReporterService.override(LeakReporterService.original)
            }
        }

        test("a closed font is not reported when the collection trigger fires") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            try {
                val font = Font.load(Roboto.variableFont)
                font.close()

                font.onCollectionObserved()

                reports shouldBe emptyList()
            } finally {
                LeakReporterService.override(LeakReporterService.original)
            }
        }
    })
