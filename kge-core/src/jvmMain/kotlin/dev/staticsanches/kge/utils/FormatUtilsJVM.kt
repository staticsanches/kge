package dev.staticsanches.kge.utils

internal actual fun Double.toHumanReadableByteCountBin(unit: String): String = "%.1f%s".format(this, unit)
