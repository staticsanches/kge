package dev.staticsanches.kge.renderer.decal.service

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The partial-decal geometry seam: [drawPartialDecal] turns a sub-rectangle of
 * a [Decal] into a four-vertex [DecalInstance]. It is stateless and pure CPU
 * math; the engine supplies the [viewport] size.
 *
 * The engine-defined default follows olc v2.30's `DrawPartialDecal` exactly:
 * the screen corners are quantised to the pixel grid so tile atlases sample
 * cleanly, and the UVs carry the `0.0001` epsilon against the sprite-size UV
 * scale. A consumer may replace the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override].
 */
interface DrawPartialDecalService : KGEOverridable {
    /**
     * Builds the four-vertex quad for the [sourceSize]-sized region at
     * [sourcePosition] of [decal]'s texture, anchored at [position] (in pixels),
     * scaled by [scale] and tinted uniformly by [tint]. [viewport] is the
     * drawable size in pixels; [mode] and [structure] are carried into the
     * instance unchanged.
     */
    fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance

    companion object :
        KGEOverridable.Proxy<DrawPartialDecalService>(DrawPartialDecalService::class, DrawPartialDecalServiceDefault),
        DrawPartialDecalService {
        override fun drawPartialDecal(
            position: Float2D,
            decal: Decal,
            sourcePosition: Float2D,
            sourceSize: Float2D,
            scale: Float2D,
            tint: Pixel,
            mode: Decal.Mode,
            structure: Decal.Structure,
            viewport: Int2D,
        ): DecalInstance =
            delegate
                .drawPartialDecal(
                    position,
                    decal,
                    sourcePosition,
                    sourceSize,
                    scale,
                    tint,
                    mode,
                    structure,
                    viewport,
                )
    }
}

/** The platform-independent default — olc `DrawPartialDecal` quantised math. */
private object DrawPartialDecalServiceDefault : DrawPartialDecalService {
    override fun drawPartialDecal(
        position: Float2D,
        decal: Decal,
        sourcePosition: Float2D,
        sourceSize: Float2D,
        scale: Float2D,
        tint: Pixel,
        mode: Decal.Mode,
        structure: Decal.Structure,
        viewport: Int2D,
    ): DecalInstance {
        val inverse = Float2D(1f / viewport.x, 1f / viewport.y)
        val screenSpacePosX = position.x * inverse.x * 2f - 1f
        val screenSpacePosY = -(position.y * inverse.y * 2f - 1f)
        val screenSpaceDimX = (position.x + sourceSize.x * scale.x) * inverse.x * 2f - 1f
        val screenSpaceDimY = -((position.y + sourceSize.y * scale.y) * inverse.y * 2f - 1f)

        val quantisedPosX = floor(screenSpacePosX * viewport.x + 0.5f) / viewport.x
        val quantisedPosY = floor(screenSpacePosY * viewport.y + 0.5f) / viewport.y
        val quantisedDimX = ceil(screenSpaceDimX * viewport.x + 0.5f) / viewport.x
        val quantisedDimY = ceil(screenSpaceDimY * viewport.y - 0.5f) / viewport.y

        val uvScale = Float2D(1f / decal.sprite.width, 1f / decal.sprite.height)
        val uvtlX = (sourcePosition.x + 0.0001f) * uvScale.x
        val uvtlY = (sourcePosition.y + 0.0001f) * uvScale.y
        val uvbrX = (sourcePosition.x + sourceSize.x - 0.0001f) * uvScale.x
        val uvbrY = (sourcePosition.y + sourceSize.y - 0.0001f) * uvScale.y

        return DecalInstance(
            decal = decal,
            pos =
                listOf(
                    Float2D(quantisedPosX, quantisedPosY),
                    Float2D(quantisedPosX, quantisedDimY),
                    Float2D(quantisedDimX, quantisedDimY),
                    Float2D(quantisedDimX, quantisedPosY),
                ),
            uv =
                listOf(
                    Float2D(uvtlX, uvtlY),
                    Float2D(uvtlX, uvbrY),
                    Float2D(uvbrX, uvbrY),
                    Float2D(uvbrX, uvtlY),
                ),
            tint = List(4) { tint },
            mode = mode,
            structure = structure,
        )
    }
}
