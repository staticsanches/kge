package dev.staticsanches.kge.overridable

actual val translatorDefault: TranslatorService =
    object : TranslatorService {
        override fun translate(message: String): String = "default:jvm:$message"
    }
