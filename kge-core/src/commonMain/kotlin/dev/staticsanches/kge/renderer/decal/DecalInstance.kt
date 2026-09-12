package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D

/**
 * One drawable unit of a [Decal]: the geometry the common draw services build
 * and the renderer consumes. It is the contract between the two, not the
 * primary user API.
 *
 * The parallel per-vertex lists — [pos] (clip space), [uv] (texture
 * coordinates) and [tint] — are snapshotted at construction, so the instance is
 * immutable. There is no separate vertex count, no `w` and no depth: the
 * geometry is 2D and the vertex count derives from the lists. The [decal] is
 * never null; untextured/3D geometry belongs to a future task type.
 */
class DecalInstance(
    /** The texture this geometry samples; never null. */
    val decal: Decal,
    pos: List<Float2D>,
    uv: List<Float2D>,
    tint: List<Pixel>,
    /** How the fragments blend with the draw target. */
    val mode: Decal.Mode,
    /** How the vertex list is assembled into primitives. */
    val structure: Decal.Structure,
) {
    /** The per-vertex clip-space positions. */
    val pos: List<Float2D> = pos.toList()

    /** The per-vertex texture coordinates. */
    val uv: List<Float2D> = uv.toList()

    /** The per-vertex tints. */
    val tint: List<Pixel> = tint.toList()

    /** The number of vertices, derived from [pos]. */
    val vertexCount: Int get() = pos.size

    init {
        require(pos.size == uv.size && pos.size == tint.size) {
            "pos, uv and tint must have the same size: ${pos.size}, ${uv.size}, ${tint.size}"
        }
    }
}
