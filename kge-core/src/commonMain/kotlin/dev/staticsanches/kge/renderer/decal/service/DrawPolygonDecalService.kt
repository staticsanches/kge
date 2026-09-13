package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance

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
        val inverse = Float2D(1f / viewport.x, 1f / viewport.y)
        return DecalInstance(
            decal = decal,
            pos =
                List(pos.size) { index ->
                    Float2D(
                        pos[index].x * inverse.x * 2f - 1f,
                        -(pos[index].y * inverse.y * 2f - 1f),
                    )
                },
            uv = uv,
            tint = tint,
            mode = mode,
            structure = structure,
        )
    }
}
