package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import kotlin.io.encoding.Base64

/** The bundled Roboto fixture, decoded from the chunked base64 the data module ships. */
fun robotoFontBytes(): ByteArray = Base64.decode(Roboto.variableFont.joinToString(""))
