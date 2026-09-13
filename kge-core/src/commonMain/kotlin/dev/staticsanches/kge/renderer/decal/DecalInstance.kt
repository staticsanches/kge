package dev.staticsanches.kge.renderer.decal

/**
 * One drawable unit of a [Decal]: the geometry the common draw services build
 * and the renderer consumes.
 *
 * The per-vertex geometry is pulled through [vertices] and the vertex count
 * derives from it; the geometry is 2D, with no `w` or depth. [decal] is never
 * null.
 */
class DecalInstance(
    /** The texture this geometry samples. */
    val decal: Decal,
    /** How the fragments blend with the draw target. */
    val mode: Decal.Mode,
    /** How the vertex list is assembled into primitives. */
    val structure: Decal.Structure,
    /** The per-vertex geometry to draw. */
    val vertices: VerticesInfo,
) {
    val vertexCount: Int get() = vertices.vertexCount
}
