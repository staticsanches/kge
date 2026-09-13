package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D

/**
 * One drawable unit of a [Decal]: the geometry the common draw services build
 * and the renderer consumes.
 *
 * The parallel per-vertex lists — [pos] (clip space), [uv] (texture
 * coordinates) and [tint] — are snapshotted at construction, so the instance is
 * immutable. The vertex count derives from the lists; the geometry is 2D, with
 * no `w` or depth. [decal] is never null.
 */
class DecalInstance(
    /** The texture this geometry samples. */
    val decal: Decal,
    pos: List<Float2D>,
    uv: List<Float2D>,
    tint: List<Pixel>,
    /** How the fragments blend with the draw target. */
    val mode: Decal.Mode,
    /** How the vertex list is assembled into primitives. */
    val structure: Decal.Structure,
) {
    val pos: List<Float2D> = pos.toList()

    val uv: List<Float2D> = uv.toList()

    val tint: List<Pixel> = tint.toList()

    val vertexCount: Int get() = pos.size

    init {
        require(pos.size == uv.size && pos.size == tint.size) {
            "pos, uv and tint must have the same size: ${pos.size}, ${uv.size}, ${tint.size}"
        }
    }
}
