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

        // A fired collection trigger drops the release action, so the font's
        // payload keeps a pending report; pin the font that leaked it.
        val pinnedLeaks = mutableListOf<Font>()

        test("an unclosed font is reported as the face, not the payload") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            try {
                val font = Font.load(Roboto.variableFont)
                pinnedLeaks += font

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

        test("a rasterized font is still reported as the face") {
            val reports = mutableListOf<String>()
            LeakReporterService.override(reporting(reports))
            try {
                val font = Font.load(Roboto.variableFont)
                pinnedLeaks += font
                val glyphs = font.shape("A", 16).glyphs
                val glyphId = glyphs.single().glyphId
                font.glyph(16, glyphId)

                font.onCollectionObserved()

                reports.single() shouldContain "font face"
            } finally {
                LeakReporterService.override(LeakReporterService.original)
            }
        }
    })
