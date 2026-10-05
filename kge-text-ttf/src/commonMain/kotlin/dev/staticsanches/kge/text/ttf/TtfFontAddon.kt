package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.engine.addon.TextAddon
import dev.staticsanches.kge.text.KGEFont

/**
 * TTF loading over a running engine: every loaded family is adopted into the
 * engine's scope, and a call's first payload is its family's default face.
 */
@OptIn(KGESensitiveAPI::class)
interface TtfFontAddon : TextAddon {
    /** Loads [bytes] as one family adopted into the engine's scope. */
    suspend fun loadFont(vararg bytes: ByteArray): KGEFont.Family =
        KGETtfFontService.createResources(resourceScope, *bytes)

    /** The chunked-base64 form of [loadFont]. */
    suspend fun loadFontBase64(vararg base64: List<String>): KGEFont.Family =
        KGETtfFontService.createResources(resourceScope, *base64)
}
