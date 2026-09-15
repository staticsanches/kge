package dev.staticsanches.kge.engine.layer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Colors
import dev.staticsanches.kge.image.Pixel
import dev.staticsanches.kge.image.Sprite
import dev.staticsanches.kge.math.vector.Float2D
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.renderer.decal.DecalInstance
import dev.staticsanches.kge.resource.KGEInternalResource

/**
 * One composited layer: the [Sprite] drawn into and the [Decal] that uploads it
 * to the GPU. The engine creates layers through the layer stack, which owns and
 * releases both resources as one unit. A layer's properties are changed on the
 * engine thread only.
 */
class Layer internal constructor(
    target: Sprite,
    decal: Decal,
) : KGEInternalResource {
    /** The surface this layer's content is drawn into. */
    var target: Sprite = target
        internal set

    /** The GPU storage of [target]. */
    var decal: Decal = decal
        internal set

    /** Whether the layer is composited this frame. */
    var show: Boolean = false

    /** Whether [decal] needs a CPU → GPU upload before compositing. */
    var update: Boolean = false

    /** The texture-space origin of the composited quad. */
    var offset: Float2D = Float2D(0f, 0f)

    /** The texture-space size of the composited quad. */
    var scale: Float2D = Float2D(1f, 1f)

    /** The tint multiplied into the composited quad. */
    var tint: Pixel = Colors.WHITE

    /**
     * Replaces the default quad rendering for this layer when set; the layer's
     * queued decal instances are not flushed in that case.
     */
    var customRender: ((Layer) -> Unit)? = null

    /** Decals queued for this layer in the current frame; flushed by the render step. */
    internal val decalInstances = mutableListOf<DecalInstance>()

    /** Installs [other]'s target and decal, marks the layer for upload and releases the previous pair. */
    internal fun replaceWith(other: Layer) {
        val previousTarget = target
        val previousDecal = decal
        target = other.target
        decal = other.decal
        update = true
        try {
            previousTarget.close()
        } finally {
            previousDecal.close()
        }
    }

    /** Releases the owned target and decal as one unit. */
    @KGESensitiveAPI
    override fun close() {
        try {
            target.close()
        } finally {
            decal.close()
        }
    }
}
