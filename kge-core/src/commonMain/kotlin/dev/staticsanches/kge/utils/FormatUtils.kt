package dev.staticsanches.kge.utils

/**
 * Formats the informed count to a human-readable size.
 */
fun Int.toHumanReadableByteCountBin() = toLong().toHumanReadableByteCountBin()

/**
 * Formats the informed count to a human-readable size.
 */
fun Long.toHumanReadableByteCountBin() =
    when {
        this == Long.MIN_VALUE || this < 0 -> "N/A"
        this < 1024L -> "${this}B"
        this <= 0xfffccccccccccccL shr 40 -> (toDouble() / (0x1 shl 10)).toHumanReadableByteCountBin("KiB")
        this <= 0xfffccccccccccccL shr 30 -> (toDouble() / (0x1 shl 20)).toHumanReadableByteCountBin("MiB")
        this <= 0xfffccccccccccccL shr 20 -> (toDouble() / (0x1 shl 30)).toHumanReadableByteCountBin("GiB")
        this <= 0xfffccccccccccccL shr 10 -> (toDouble() / (0x1 shl 40)).toHumanReadableByteCountBin("TiB")
        this <= 0xfffccccccccccccL -> ((this shr 10).toDouble() / (0x1 shl 40)).toHumanReadableByteCountBin("PiB")
        else -> ((this shr 20).toDouble() / (0x1 shl 40)).toHumanReadableByteCountBin("EiB")
    }

internal expect fun Double.toHumanReadableByteCountBin(unit: String): String
