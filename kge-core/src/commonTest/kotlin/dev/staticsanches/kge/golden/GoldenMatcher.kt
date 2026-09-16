package dev.staticsanches.kge.golden

import dev.staticsanches.kge.image.Pixmap
import kotlin.io.encoding.Base64

private const val MAX_TOKEN_CELLS = 4096

/**
 * Asserts that this surface is pixel-identical to the golden image [name]
 * (path relative to `golden/`, without extension). Unknown names, a size
 * disagreeing with the golden, or any differing cell throw [AssertionError].
 * A mismatch reports the count and, at or below [MAX_TOKEN_CELLS] cells,
 * carries the actual surface as a `WxH:<base64>` token (RGBA, ready for
 * `goldenActualToPng`); above the budget the token is omitted.
 */
fun Pixmap.shouldMatchGolden(name: String) {
    val golden =
        GoldenImages.byName[name]
            ?: throw AssertionError(
                "unknown golden \"$name\"; available: ${GoldenImages.byName.keys.sorted().joinToString(", ")}",
            )
    if (width != golden.width || height != golden.height) {
        throw AssertionError(
            "golden \"$name\" is ${golden.width}x${golden.height} but the surface is ${width}x$height",
        )
    }

    val reference = Base64.Default.decode(golden.rgbaBase64)
    val cells = width * height
    val actual = if (cells <= MAX_TOKEN_CELLS) ByteArray(reference.size) else null
    var index = 0
    var mismatches = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val native = get(x, y).nativeRGBA
            if (actual != null) {
                actual[index] = (native and 0xFF).toByte()
                actual[index + 1] = ((native ushr 8) and 0xFF).toByte()
                actual[index + 2] = ((native ushr 16) and 0xFF).toByte()
                actual[index + 3] = ((native ushr 24) and 0xFF).toByte()
            }
            if (native != nativeAt(reference, index)) mismatches++
            index += 4
        }
    }
    if (mismatches != 0) {
        throw AssertionError(mismatchMessage(name, golden, mismatches, actual))
    }
}

private fun mismatchMessage(
    name: String,
    golden: GoldenImage,
    mismatches: Int,
    actual: ByteArray?,
): String {
    val summary =
        "golden \"$name\" (${golden.width}x${golden.height}): " +
            "$mismatches mismatch${if (mismatches == 1) "" else "es"}"
    return if (actual != null) {
        "$summary\n${golden.width}x${golden.height}:${Base64.Default.encode(actual)}"
    } else {
        val cells = golden.width * golden.height
        "$summary; actual token omitted ($cells cells > $MAX_TOKEN_CELLS-cell budget)"
    }
}

private fun nativeAt(
    bytes: ByteArray,
    offset: Int,
): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
