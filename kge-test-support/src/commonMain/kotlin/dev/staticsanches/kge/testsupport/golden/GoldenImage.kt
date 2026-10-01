package dev.staticsanches.kge.testsupport.golden

/** A committed reference image: [rgbaBase64] holds [width]x[height] R,G,B,A bytes. */
class GoldenImage(
    val name: String,
    val width: Int,
    val height: Int,
    val rgbaBase64: String,
)
