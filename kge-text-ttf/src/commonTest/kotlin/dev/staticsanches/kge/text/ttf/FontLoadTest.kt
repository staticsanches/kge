package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

/**
 * The public entry points into a face: decoded bytes or the chunked base64 the
 * bundled data module emits, and a payload that is not a usable font failing
 * fast on both.
 */
class FontLoadTest :
    FunSpec({
        test("the bundled Roboto fixture loads from decoded bytes") {
            Font.load(robotoFontBytes()).use { }
        }

        test("the bundled Roboto fixture loads from the chunked base64") {
            Font.load(Roboto.variableFont).use { }
        }

        test("bytes that are not a font fail fast") {
            shouldThrow<IllegalArgumentException> { Font.load(ByteArray(128) { (it * 31).toByte() }) }
            shouldThrow<IllegalArgumentException> { Font.load(listOf("Zm9vYmFy")) }
        }
    })
