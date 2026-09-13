package dev.staticsanches.kge.renderer.gl.resource

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.renderer.gl.GL
import dev.staticsanches.kge.renderer.gl.GLTexture
import dev.staticsanches.kge.renderer.gl.GLenum
import dev.staticsanches.kge.resource.KGECleanAction
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import dev.staticsanches.kge.resource.onCollectionObserved

/**
 * The GPU storage of a [Sprite], owned through the T1 resource contract.
 *
 * [create] allocates an RGBA8 texture and sets its raw GL filter/wrap
 * parameters — the typed mapping belongs to the renderer. [update] uploads a
 * sprite's pixels (CPU → GPU), [read] reads them back (GPU → CPU), and [apply]
 * binds the texture for the next draw.
 *
 * The class owns its GL object: [close] deletes it exactly once and every
 * operation afterwards fails fast; an unclosed instance is reported by the leak
 * detector. The constructor is the seam for an external backend creating a
 * wrapper around a handle it owns; production code creates through [create].
 */
class Texture
    @KGESensitiveAPI
    constructor(
        private val wrapper: ResourceWrapper<GLTexture>,
    ) : KGEResource by wrapper {
        /** Uploads all of [sprite]'s pixels into the whole texture (CPU → GPU). */
        fun update(sprite: Sprite) {
            GL.bindTexture(GL.TEXTURE_2D, wrapper.resource)
            GL.texSubImage2D(
                GL.TEXTURE_2D,
                0,
                0,
                0,
                sprite.width,
                sprite.height,
                GL.RGBA,
                GL.UNSIGNED_BYTE,
                sprite.buffer,
            )
        }

        /** Reads the whole texture back into [sprite] (GPU → CPU). */
        fun read(sprite: Sprite) {
            GL.bindTexture(GL.TEXTURE_2D, wrapper.resource)
            GL.getTexImage(
                GL.TEXTURE_2D,
                0,
                sprite.width,
                sprite.height,
                GL.RGBA,
                GL.UNSIGNED_BYTE,
                sprite.buffer,
            )
        }

        /** Binds this texture for the next draw. */
        fun apply() = GL.bindTexture(GL.TEXTURE_2D, wrapper.resource)

        /** Deterministic test seam: fires the platform collection trigger. */
        internal fun onCollectionObserved() = wrapper.onCollectionObserved()

        companion object {
            /**
             * Creates a [width]x[height] RGBA8 texture with [filter] on both the
             * magnification and minification filters and [wrap] on both axes;
             * [name] is the resource's diagnostic label. The GL object is deleted
             * on [close], and also if setup fails, so a failed creation never
             * leaks it.
             */
            fun create(
                width: Int,
                height: Int,
                filter: GLenum,
                wrap: GLenum,
                name: String? = null,
            ): Texture {
                require(width > 0 && height > 0) { "width and height must be positive: ${width}x$height" }
                val handle = GL.createTexture()
                val representation = "texture (${width}x$height${name?.let { ", \"$it\"" } ?: ""})"
                return ResourceWrapper(
                    representation,
                    handle,
                    KGECleanAction { GL.deleteTexture(handle) },
                ).letClosingIfFailed { wrapper ->
                    GL.bindTexture(GL.TEXTURE_2D, wrapper.resource)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, filter)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, filter)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, wrap)
                    GL.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, wrap)
                    GL.texImage2D(
                        GL.TEXTURE_2D,
                        0,
                        GL.RGBA,
                        width,
                        height,
                        0,
                        GL.RGBA,
                        GL.UNSIGNED_BYTE,
                        null,
                    )
                    Texture(wrapper)
                }
            }
        }
    }
