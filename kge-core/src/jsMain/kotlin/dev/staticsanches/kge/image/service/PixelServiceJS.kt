package dev.staticsanches.kge.image.service

actual fun reverseBytes(value: Int): Int =
    ((value and 0xff) shl 24) or
        ((value and 0xff00) shl 8) or
        ((value and 0xff0000) ushr 8) or
        ((value ushr 24) and 0xFF)
