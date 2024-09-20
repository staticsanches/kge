@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.math.vector.FloatOneByOne
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.resource.IntResource
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.applyAndCloseIfFailed

/**
 * A GPU resident storage of a [Sprite].
 */
class Decal private constructor(
    val sprite: Sprite,
    private val texture: IntResource,
) : KGEResource by texture {
    val id: Int by texture::id

    val uvScale: Float2D = FloatOneByOne / sprite.size

    fun update() = Renderer.updateTexture(id, sprite)

    fun updateSprite() = Renderer.readTexture(id, sprite)

    override fun toString(): String = texture.toString()

    enum class Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL, ILLUMINATE, WIREFRAME }

    enum class Structure { LINE, FAN, STRIP, LIST }

    companion object {
        operator fun invoke(
            sprite: Sprite,
            filtered: Boolean = false,
            clamp: Boolean = true,
        ): Decal =
            Decal(
                sprite,
                IntResource(
                    "Decal",
                    { Renderer.createTexture(filtered, clamp) },
                    ::DeleteTextureAction,
                ),
            ).applyAndCloseIfFailed { Renderer.initializeTexture(it.id, it.sprite) }
    }
}

@JvmInline
private value class DeleteTextureAction(
    val id: Int,
) : KGECleanAction {
    override fun invoke() = Renderer.deleteTexture(id)
}
