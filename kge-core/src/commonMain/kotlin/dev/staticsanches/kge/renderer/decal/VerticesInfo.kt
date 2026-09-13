package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.image.Pixel

/**
 * Pull access to a [DecalInstance]'s per-vertex geometry: the consumer reads one
 * vertex at a time and owns the byte layout. Positions are clip space and `u`/`v`
 * are texture coordinates; the geometry is 2D, with no `z`/`w`.
 */
interface VerticesInfo {
    /** The number of vertices. */
    val vertexCount: Int

    /** The clip-space x of vertex [index]. */
    fun x(index: Int): Float

    /** The clip-space y of vertex [index]. */
    fun y(index: Int): Float

    /** The texture u of vertex [index]. */
    fun u(index: Int): Float

    /** The texture v of vertex [index]. */
    fun v(index: Int): Float

    /** The tint of vertex [index]. */
    fun tint(index: Int): Pixel

    companion object
}

/**
 * A four-vertex quad in olc's order — top-left, bottom-left, bottom-right,
 * top-right: `(posX, posY)` and `(dimX, dimY)` are the opposing corners in clip
 * space, `(u0, v0)`/`(u1, v1)` the matching texture coordinates, and [tint]
 * applies to every vertex.
 */
internal fun VerticesInfo.Companion.quad(
    posX: Float,
    posY: Float,
    dimX: Float,
    dimY: Float,
    u0: Float,
    v0: Float,
    u1: Float,
    v1: Float,
    tint: Pixel,
): VerticesInfo =
    object : VerticesInfo {
        override val vertexCount: Int get() = 4

        override fun x(index: Int): Float = if (index == 0 || index == 1) posX else dimX

        override fun y(index: Int): Float = if (index == 0 || index == 3) posY else dimY

        override fun u(index: Int): Float = if (index == 0 || index == 1) u0 else u1

        override fun v(index: Int): Float = if (index == 0 || index == 3) v0 else v1

        override fun tint(index: Int): Pixel = tint
    }
