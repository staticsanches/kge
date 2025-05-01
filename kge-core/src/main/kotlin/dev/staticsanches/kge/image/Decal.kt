@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.resource.IntResource
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.applyAndCloseIfFailed

/**
 * A GPU resident storage of a texture.
 */
abstract class Decal private constructor(
    private val texture: IntResource,
    val size: Int2D,
) : KGEResource by texture {
    val id: Int by texture::id
    val uvScale: Float2D = Float2D.oneByOne / size

    constructor(size: Int2D, filtered: Boolean = false, clamp: Boolean = true) : this(
        IntResource(
            "Decal",
            { Renderer.createTexture(filtered, clamp) },
            ::DeleteTextureAction,
        ),
        size,
    )

    override fun toString(): String = texture.toString()

    enum class Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL, ILLUMINATE, WIREFRAME }

    enum class Structure { LINE, FAN, STRIP, LIST }
}

/**
 * A GPU resident storage of a [Sprite].
 */
class SpriteDecal(
    val sprite: Sprite,
    filtered: Boolean = false,
    clamp: Boolean = true,
) : Decal(sprite.size, filtered, clamp) {
    init {
        applyAndCloseIfFailed { Renderer.initializeTexture(id, sprite) }
    }

    fun update() = Renderer.updateTexture(id, sprite)

    fun updateSprite() = Renderer.readTexture(id, sprite)
}

@JvmInline
private value class DeleteTextureAction(
    val id: Int,
) : KGECleanAction {
    override fun invoke() = Renderer.deleteTexture(id)
}
