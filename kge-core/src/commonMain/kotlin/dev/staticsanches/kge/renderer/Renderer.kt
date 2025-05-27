package dev.staticsanches.kge.renderer

import dev.staticsanches.kge.extensible.KGEExtensibleService
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.applyAndCloseIfFailed
import dev.staticsanches.kge.resource.toCleanerProvider

interface Renderer : KGEExtensibleService {
    fun createTexture(
        name: String?,
        filtered: Boolean,
        clamp: Boolean,
    ): ResourceWrapper<GLTexture>

    fun updateTexture(
        texture: GLTexture,
        sprite: Sprite,
    )

    fun readTexture(
        texture: GLTexture,
        sprite: Sprite,
    )

    fun applyTexture(texture: GLTexture?)

    companion object : Renderer by KGEExtensibleService.getOptionalWithHigherPriority()
        ?: originalRendererImplementation
}

val originalRendererImplementation: Renderer
    get() = DefaultRenderer

private data object DefaultRenderer : Renderer {
    override fun createTexture(
        name: String?,
        filtered: Boolean,
        clamp: Boolean,
    ): ResourceWrapper<GLTexture> =
        ResourceWrapper({ name ?: "GLTexture $it" }, GL::createTexture, GL::deleteTexture.toCleanerProvider())
            .applyAndCloseIfFailed { (texture) ->
                GL.bindTexture(GL.TEXTURE_2D, texture)

                if (filtered) {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
                } else {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)
                }

                if (clamp) {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
                } else {
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.REPEAT)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.REPEAT)
                }
            }

    override fun updateTexture(
        texture: GLTexture,
        sprite: Sprite,
    ) {
        GL.bindTexture(GL.TEXTURE_2D, texture)
        GL.texImage2D(
            GL.TEXTURE_2D,
            0,
            GL.RGBA,
            sprite.width,
            sprite.height,
            0,
            GL.RGBA,
            GL.UNSIGNED_BYTE,
            sprite.resource.clear(),
        )
    }

    override fun readTexture(
        texture: GLTexture,
        sprite: Sprite,
    ) {
        GL.bindTexture(GL.TEXTURE_2D, texture)
        GL.readPixels(0, 0, sprite.width, sprite.height, GL.RGBA, GL.UNSIGNED_BYTE, sprite.resource.clear())
    }

    override fun applyTexture(texture: GLTexture?) = GL.bindTexture(GL.TEXTURE_2D, texture)

    override val servicePriority: Int
        get() = Int.MIN_VALUE
}
