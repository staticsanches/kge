package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.VerticesInfo

/**
 * The arbitrary textured-polygon geometry seam: [drawPolygonDecal] turns a
 * caller-supplied vertex/UV/tint list over a [Decal] into an N-vertex
 * [DecalInstance].
 *
 * The default follows olc v2.30's `DrawPolygonDecal`: the vertices are converted
 * to clip space (y flipped, scaled by the viewport) and the UVs and tints are
 * taken per vertex as given. A consumer may replace the whole behavior for the
 * process via [override][KGEOverridable.Proxy.override].
 */
interface DrawPolygonDecalService : KGEOverridable {
    /**
     * Builds one vertex per entry of [pos], with the parallel [uv] and [tint]
     * entries. [pos] are screen-space pixels; [uv] are texture coordinates as
     * given. [viewport] is the drawable size; [mode] and [structure] are carried
     * unchanged.
     */
    fun drawPolygonDecal(
        decal: Decal,
        pos: List<Float2D>,
        uv: List<Float2D>,
        tint: List<Pixel>,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance

    companion object :
        KGEOverridable.Proxy<DrawPolygonDecalService>(DrawPolygonDecalService::class, DrawPolygonDecalServiceDefault),
        DrawPolygonDecalService {
        override fun drawPolygonDecal(
            decal: Decal,
            pos: List<Float2D>,
            uv: List<Float2D>,
            tint: List<Pixel>,
            mode: Decal.Mode,
            structure: Decal.Structure,
            viewport: Int2D,
        ): DecalInstance = delegate.drawPolygonDecal(decal, pos, uv, tint, mode, structure, viewport)
    }
}

/**
 * The polygon geometry in primitive, construction-copied storage: one
 * interleaved `x, y, u, v` float per vertex plus little-endian RGBA tints.
 */
private fun polygonVertices(
    vertices: FloatArray,
    tints: IntArray,
): VerticesInfo =
    object : VerticesInfo {
        override val vertexCount: Int get() = vertices.size / 4

        override fun x(index: Int): Float = vertices[index * 4]

        override fun y(index: Int): Float = vertices[index * 4 + 1]

        override fun u(index: Int): Float = vertices[index * 4 + 2]

        override fun v(index: Int): Float = vertices[index * 4 + 3]

        override fun tint(index: Int): Pixel = Pixel.fromNativeRGBA(tints[index])
    }

/** The platform-independent default — olc `DrawPolygonDecal` clip-space math. */
private object DrawPolygonDecalServiceDefault : DrawPolygonDecalService {
    override fun drawPolygonDecal(
        decal: Decal,
        pos: List<Float2D>,
        uv: List<Float2D>,
        tint: List<Pixel>,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        require(pos.size == uv.size && pos.size == tint.size) {
            "pos, uv and tint must have the same size: ${pos.size}, ${uv.size}, ${tint.size}"
        }
        val inverse = Float2D(1f / viewport.x, 1f / viewport.y)
        val vertices = FloatArray(pos.size * 4)
        for (index in 0 until pos.size) {
            val offset = index * 4
            vertices[offset] = pos[index].x * inverse.x * 2f - 1f
            vertices[offset + 1] = -(pos[index].y * inverse.y * 2f - 1f)
            vertices[offset + 2] = uv[index].x
            vertices[offset + 3] = uv[index].y
        }
        return DecalInstance(
            decal = decal,
            mode = mode,
            structure = structure,
            vertices =
                polygonVertices(
                    vertices = vertices,
                    tints = IntArray(tint.size) { index -> tint[index].nativeRGBA },
                ),
        )
    }
}
