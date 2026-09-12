package dev.staticsanches.kge.renderer.decal

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The GPU storage of a [Sprite], owned through the T1 resource contract.
 *
 * A decal carries no geometry: it owns the [texture] uploaded from [sprite] and
 * keeps a reference to the CPU side so [update] (CPU → GPU) and [updateSprite]
 * (GPU → CPU) can reach the pixels. The `Sprite` is not owned by the decal and
 * is not closed with it.
 *
 * The texture is created, uploaded, bound and deleted through the overridable
 * [Renderer] — there is no decal-specific service. Because the owned [Texture]
 * is itself a registered T1 resource, a decal that is never [close]d surfaces as
 * a leaked texture; [close] is the single release path.
 *
 * The [Mode]/[Structure]/[Filter]/[Wrap] values are the typed vocabulary the
 * renderer consumes; mapping them to GL belongs to the renderer.
 *
 * The constructor is a resource seam for an external backend that already owns
 * a [Texture]; production code creates through the companion factory.
 */
class Decal
    @KGESensitiveAPI
    constructor(
        textureResource: Texture,
        /**
         * The CPU surface this decal mirrors. Not owned: kept only so that [update]
         * and [updateSprite] can reach the pixels.
         */
        val sprite: Sprite,
    ) : KGEResource {
        /**
         * The GPU storage this decal owns and binds before drawing the
         * [DecalInstance]s that reference it. Exposed so an external renderer
         * implementer can bind it; the decal keeps T1 ownership and [close]
         * remains the only release path.
         */
        @KGESensitiveAPI
        val texture: Texture = textureResource

        /** How a decal's fragments blend with the draw target. */
        enum class Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL, ILLUMINATE, WIREFRAME }

        /** How a [DecalInstance]'s vertex list is assembled into primitives. */
        enum class Structure { LINE, FAN, STRIP, LIST }

        /** The texture magnification/minification filter. */
        enum class Filter { NEAREST, LINEAR }

        /** The texture edge behavior outside `[0, 1]`. */
        enum class Wrap { CLAMP_TO_EDGE, REPEAT }

        /** Re-uploads the referenced [sprite] to the GPU (CPU → GPU). */
        fun update() = Renderer.updateTexture(texture, sprite)

        /** Reads the GPU texture back into the referenced [sprite] (GPU → CPU). */
        fun updateSprite() = Renderer.readTexture(texture, sprite)

        /**
         * Builds the [DecalPatch] for the [size]-sized region at [pos], both in
         * pixels of the decal's texture. The coordinates are bottom-left,
         * top-left, top-right, bottom-right (olc's `Decal::Patch`).
         */
        fun patch(
            pos: Int2D,
            size: Int2D,
        ): DecalPatch {
            val spriteSize = Float2D(sprite.width.toFloat(), sprite.height.toFloat())
            return DecalPatch(
                decal = this,
                coords =
                    listOf(
                        Float2D(pos.x.toFloat(), (pos.y + size.y).toFloat()) / spriteSize,
                        Float2D(pos.x.toFloat(), pos.y.toFloat()) / spriteSize,
                        Float2D((pos.x + size.x).toFloat(), pos.y.toFloat()) / spriteSize,
                        Float2D((pos.x + size.x).toFloat(), (pos.y + size.y).toFloat()) / spriteSize,
                    ),
            )
        }

        /** Builds the [DecalPatch] from explicit BL/TL/TR/BR texture coordinates. */
        fun patch(
            bl: Float2D,
            tl: Float2D,
            tr: Float2D,
            br: Float2D,
        ): DecalPatch = DecalPatch(this, listOf(bl, tl, tr, br))

        /** Deletes the owned texture exactly once; every use afterwards fails fast (T1). */
        override fun close() = texture.close()

        companion object {
            /**
             * Creates a decal over [sprite] with the given sampling [filter] and
             * edge [wrap] behavior, and uploads the pixels in the constructor.
             *
             * The texture is created through the overridable [Renderer] and is
             * deleted when the decal is [close]d, and also when the initial upload
             * below fails, so a failed creation never leaks it
             * ([letClosingIfFailed]).
             */
            operator fun invoke(
                sprite: Sprite,
                filter: Filter,
                wrap: Wrap,
            ): Decal =
                Renderer
                    .createTexture(sprite.width, sprite.height, filter, wrap, sprite.name)
                    .letClosingIfFailed { texture ->
                        Renderer.updateTexture(texture, sprite)
                        Decal(texture, sprite)
                    }
        }
    }
