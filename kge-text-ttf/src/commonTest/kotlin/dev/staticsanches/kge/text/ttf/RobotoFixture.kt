package dev.staticsanches.kge.text.ttf

import dev.staticsanches.kge.font.roboto.Roboto
import dev.staticsanches.kge.font.roboto.RobotoMono
import kotlin.io.encoding.Base64

/** The bundled Roboto fixture, decoded from the chunked base64 the data module ships. */
fun robotoFontBytes(): ByteArray = Base64.decode(Roboto.romanFont.joinToString(""))

/** The bundled Roboto italic fixture, decoded from its chunked base64. */
fun robotoItalicBytes(): ByteArray = Base64.decode(Roboto.italicFont.joinToString(""))

/** The bundled Roboto Mono roman fixture, decoded from its chunked base64. */
fun robotoMonoBytes(): ByteArray = Base64.decode(RobotoMono.romanFont.joinToString(""))

/** The bundled Roboto Mono italic fixture, decoded from its chunked base64. */
fun robotoMonoItalicBytes(): ByteArray = Base64.decode(RobotoMono.italicFont.joinToString(""))
