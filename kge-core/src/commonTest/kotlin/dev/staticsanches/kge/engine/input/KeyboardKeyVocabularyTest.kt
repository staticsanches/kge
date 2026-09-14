package dev.staticsanches.kge.engine.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The common keyboard vocabulary is the intersection of the platform key sets:
 * every [KeyVocabulary] entry must resolve to a real platform member (never the
 * platform's last-entry fallback) and to a distinct one.
 */
class KeyboardKeyVocabularyTest :
    FunSpec({
        test("every vocabulary key maps to a real platform member") {
            val fallback = KeyboardKey.entries.last()
            for (vocabulary in KeyVocabulary.entries) {
                keyboardKey(vocabulary) shouldNotBe fallback
            }
        }

        test("distinct vocabulary keys map to distinct platform members") {
            val mapped = KeyVocabulary.entries.map { keyboardKey(it) }
            mapped.toSet().size shouldBe mapped.size
        }

        test("the companion extensions expose the same mapping") {
            KeyboardKey.escape shouldBe keyboardKey(KeyVocabulary.Escape)
            KeyboardKey.a shouldBe keyboardKey(KeyVocabulary.A)
            KeyboardKey.k0 shouldBe keyboardKey(KeyVocabulary.K0)
            KeyboardKey.pageUp shouldBe keyboardKey(KeyVocabulary.PageUp)
            KeyboardKey.numpad0 shouldBe keyboardKey(KeyVocabulary.Numpad0)
            KeyboardKey.oem1 shouldBe keyboardKey(KeyVocabulary.Oem1)
        }
    })
