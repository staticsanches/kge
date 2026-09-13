package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.decal.DecalPatch
import dev.staticsanches.kge.renderer.decal.VerticesInfo
import dev.staticsanches.kge.renderer.decal.quad

/**
 * The full-decal geometry seam: [drawDecal] turns a screen-space position,
 * scale and tint into a four-vertex [DecalInstance] over the whole decal, and
 * its [DecalPatch] overload transforms the patch's four coordinates into the
 * polygon path.
 *
 * The default follows olc v2.30's `DrawDecal`; a consumer may replace the whole
 * behavior for the process via [override][KGEOverridable.Proxy.override].
 */
interface DrawDecalService : KGEOverridable {
    /**
     * Builds the four-vertex quad for [decal] anchored at [position] (its
     * top-left, in pixels), scaled by [scale] and tinted uniformly by [tint].
     * [viewport] is the drawable size; [mode] and [structure] are carried
     * unchanged.
     */
    fun drawDecal(
        position: Float2D,
        decal: Decal,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance

    /**
     * Builds the polygon instance for [patch] anchored at [position] (its
     * top-left, in pixels), scaled by [scale]. The patch coordinates are
     * transformed into screen vertices and fed to [DrawPolygonDecalService] with
     * a uniform white tint. [viewport] is the drawable size; [mode] and
     * [structure] are carried unchanged.
     */
    fun drawDecal(
        position: Float2D,
        patch: DecalPatch,
        scale: Float2D,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance

    companion object :
        KGEOverridable.Proxy<DrawDecalService>(DrawDecalService::class, DrawDecalServiceDefault),
        DrawDecalService {
        override fun drawDecal(
            position: Float2D,
            decal: Decal,
            scale: Float2D,
            tint: Pixel,
            mode: Decal.Mode,
            structure: Decal.Structure,
            viewport: Int2D,
        ): DecalInstance = delegate.drawDecal(position, decal, scale, tint, mode, structure, viewport)

        override fun drawDecal(
            position: Float2D,
            patch: DecalPatch,
            scale: Float2D,
            mode: Decal.Mode,
            structure: Decal.Structure,
            viewport: Int2D,
        ): DecalInstance = delegate.drawDecal(position, patch, scale, mode, structure, viewport)
    }
}

/** The platform-independent default — olc `DrawDecal` clip-space math. */
private object DrawDecalServiceDefault : DrawDecalService {
    override fun drawDecal(
        position: Float2D,
        decal: Decal,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        val inverse = Float2D(1f / viewport.x, 1f / viewport.y)
        val screenSpacePosX = position.x * inverse.x * 2f - 1f
        val screenSpacePosY = -(position.y * inverse.y * 2f - 1f)
        val screenSpaceDimX = screenSpacePosX + 2f * decal.sprite.width * inverse.x * scale.x
        val screenSpaceDimY = screenSpacePosY - 2f * decal.sprite.height * inverse.y * scale.y

        return DecalInstance(
            decal = decal,
            mode = mode,
            structure = structure,
            vertices =
                VerticesInfo.quad(
                    screenSpacePosX,
                    screenSpacePosY,
                    screenSpaceDimX,
                    screenSpaceDimY,
                    0f,
                    0f,
                    1f,
                    1f,
                    tint,
                ),
        )
    }

    override fun drawDecal(
        position: Float2D,
        patch: DecalPatch,
        scale: Float2D,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        val vertices =
            listOf(
                Float2D(position.x, position.y + scale.y),
                position,
                Float2D(position.x + scale.x, position.y),
                position + scale,
            )
        return DrawPolygonDecalService.drawPolygonDecal(
            decal = patch.decal,
            pos = vertices,
            uv = listOf(patch.bl, patch.tl, patch.tr, patch.br),
            tint = List(4) { Colors.WHITE },
            mode = mode,
            structure = structure,
            viewport = viewport,
        )
    }
}
