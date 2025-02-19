@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.FloatOneByOne
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.Texture
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.applyAndCloseIfFailed

/**
 * A GPU resident storage of a texture.
 */
interface Decal : KGEResource {
    val id: Int
    val textureDimension: Int2D
    val uvScale: Float2D

    enum class Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL, ILLUMINATE, WIREFRAME }

    enum class Structure { LINE, FAN, STRIP, LIST }
}

/**
 * A limited view of a [Decal].
 */
interface PartialDecal :
    Decal,
    Viewport.Bounded

/**
 * A GPU resident storage of a [Sprite].
 */
class SpriteDecal private constructor(
    private val texture: Texture,
    val sprite: Sprite,
) : PartialDecal,
    Viewport.Bounded by sprite,
    KGEResource by texture {
    constructor(
        sprite: Sprite,
        filtered: Boolean = false,
        clamp: Boolean = true,
    ) : this(Texture("Decal of $sprite", filtered, clamp), sprite)

    override val id: Int by texture::id
    override val textureDimension: Int2D by sprite::size
    override val uvScale: Float2D = FloatOneByOne / textureDimension

    init {
        applyAndCloseIfFailed { Renderer.initializeTexture(id, sprite) }
    }

    fun update() = Renderer.updateTexture(id, sprite)

    fun updateSprite() = Renderer.readTexture(id, sprite)

    override fun toString(): String = texture.toString()
}
