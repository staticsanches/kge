package dev.staticsanches.kge.overridable

internal actual val translatorDefault: TranslatorService =
    object : TranslatorService {
        override fun translate(message: String): String = "default:jvm:$message"
    }
