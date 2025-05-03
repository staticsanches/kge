package dev.staticsanches.kge.utils

internal actual fun Double.toHumanReadableByteCountBin(unit: String): String = asDynamic().toFixed(1) + " $unit"
