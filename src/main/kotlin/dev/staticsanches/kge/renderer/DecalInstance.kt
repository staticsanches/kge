package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.image.Decal
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D

class DecalInstance(
    val decal: Decal?,
    val mode: Decal.Mode,
    val structure: Decal.Structure,
    vararg points: PointInfo,
) {
    val points: List<PointInfo> = points.toList()

    init {
        check(this.points.isNotEmpty()) { "Invalid number of points" }
    }

    data class PointInfo(
        val position: Float2D,
        val uv: Float2D,
        val w: Float,
        val tint: Pixel,
    )
}
