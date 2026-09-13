package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.renderer.device.GpuDevice
import dev.staticsanches.kge.renderer.gl.resource.Texture
import dev.staticsanches.kge.renderer.internal.rendererDefault
import dev.staticsanches.kge.resource.ResourceScope

/**
 * The typed drawing surface over the raw, overridable
 * [dev.staticsanches.kge.renderer.gl.service.GLService].
 *
 * The renderer is stateless: it holds no GPU objects. Its built-in GPU
 * resources live in the [ResourceScope] the engine owns and closes;
 * [createResources] builds them once at startup while a context is current,
 * and every draw resolves them through the same scope. A consumer may replace
 * the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override].
 */
interface Renderer : KGEOverridable {
    /**
     * Builds the renderer's built-in GPU resources into [scope]. Called once by
     * the engine at startup with a current context; the scope owns and closes
     * them, making [device]'s context current on release.
     */
    fun createResources(
        device: GpuDevice,
        scope: ResourceScope,
    )

    /**
     * Creates a [width]x[height] RGBA8 [Texture] with the given sampling
     * [filter] and edge [wrap] behavior; [name] is its diagnostic label. The
     * caller owns it and closes it through [Texture.close].
     */
    fun createTexture(
        width: Int,
        height: Int,
        filter: Decal.Filter,
        wrap: Decal.Wrap,
        name: String? = null,
    ): Texture

    /** Uploads all of [sprite]'s pixels into [texture] (CPU → GPU). */
    fun updateTexture(
        texture: Texture,
        sprite: Sprite,
    )

    /** Reads [texture]'s pixels back into [sprite] (GPU → CPU). */
    fun readTexture(
        texture: Texture,
        sprite: Sprite,
    )

    /** Binds [texture] for the next draw. */
    fun applyTexture(texture: Texture)

    /** Sets the frame state: the built-in program, its VAO, blending on. */
    fun prepareDrawing(scope: ResourceScope)

    /**
     * Draws the full-screen layer quad sampling [offset]/[scale] in texture
     * space, tinted by [tint].
     */
    fun drawLayerQuad(
        scope: ResourceScope,
        offset: Float2D,
        scale: Float2D,
        tint: Pixel,
    )

    /** Draws [instance]'s geometry, one draw for the whole instance. */
    fun drawDecal(
        scope: ResourceScope,
        instance: DecalInstance,
    )

    /** Clears the color buffer (and the depth buffer when [depth] is true) to [color]. */
    fun clearBuffer(
        color: Pixel,
        depth: Boolean,
    )

    /** Sets the drawable viewport to [position] and [size]. */
    fun updateViewport(
        position: Int2D,
        size: Int2D,
    )

    companion object :
        KGEOverridable.Proxy<Renderer>(Renderer::class, rendererDefault),
        Renderer {
        override fun createResources(
            device: GpuDevice,
            scope: ResourceScope,
        ) = delegate.createResources(device, scope)

        override fun createTexture(
            width: Int,
            height: Int,
            filter: Decal.Filter,
            wrap: Decal.Wrap,
            name: String?,
        ): Texture = delegate.createTexture(width, height, filter, wrap, name)

        override fun updateTexture(
            texture: Texture,
            sprite: Sprite,
        ) = delegate.updateTexture(texture, sprite)

        override fun readTexture(
            texture: Texture,
            sprite: Sprite,
        ) = delegate.readTexture(texture, sprite)

        override fun applyTexture(texture: Texture) = delegate.applyTexture(texture)

        override fun prepareDrawing(scope: ResourceScope) = delegate.prepareDrawing(scope)

        override fun drawLayerQuad(
            scope: ResourceScope,
            offset: Float2D,
            scale: Float2D,
            tint: Pixel,
        ) = delegate.drawLayerQuad(scope, offset, scale, tint)

        override fun drawDecal(
            scope: ResourceScope,
            instance: DecalInstance,
        ) = delegate.drawDecal(scope, instance)

        override fun clearBuffer(
            color: Pixel,
            depth: Boolean,
        ) = delegate.clearBuffer(color, depth)

        override fun updateViewport(
            position: Int2D,
            size: Int2D,
        ) = delegate.updateViewport(position, size)
    }
}
