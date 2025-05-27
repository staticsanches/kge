@file:Suppress("unused")

package dev.staticsanches.kge.image

import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.Renderer
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyAndCloseIfFailed

/**
 * A GPU resident storage of a [Sprite].
 */
class Decal(
    private val textureWrapper: ResourceWrapper<GLTexture>,
    val sprite: Sprite,
) : ResourceWrapper<GLTexture> by textureWrapper {
    val uvScale: Float2D = Float2D.oneByOne / sprite.size

    fun update() = Renderer.updateTexture(resource, sprite)

    fun updateSprite() = Renderer.readTexture(resource, sprite)

    override fun toString(): String = textureWrapper.toString()

    enum class Mode { NORMAL, ADDITIVE, MULTIPLICATIVE, STENCIL, ILLUMINATE, WIREFRAME }

    enum class Structure { LINE, FAN, STRIP, LIST }

    companion object {
        operator fun invoke(
            sprite: Sprite,
            filtered: Boolean = false,
            clamp: Boolean = true,
            name: String = "Decal of $sprite",
        ): Decal =
            Decal(Renderer.createTexture(name, filtered = filtered, clamp = clamp), sprite)
                .applyAndCloseIfFailed { it.update() }
    }
}
