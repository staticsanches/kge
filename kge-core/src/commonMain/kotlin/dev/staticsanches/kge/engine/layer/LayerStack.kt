package dev.staticsanches.kge.engine.layer

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.image.Pixmap
import dev.staticsanches.kge.image.SpriteService
import dev.staticsanches.kge.renderer.decal.Decal
import dev.staticsanches.kge.resource.CompositeResource
import dev.staticsanches.kge.resource.KGEInternalResource
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * The engine's ordered layers, owned and released through the resource
 * contract. It is created with layer 0, so the stack always has a draw target;
 * each layer allocates a `Sprite` + `Decal` at the stack's screen size.
 */
class LayerStack internal constructor(
    private var screenWidth: Int,
    private var screenHeight: Int,
) : KGEInternalResource {
    private val layers = CompositeResource(allocate(screenWidth, screenHeight))

    /** The number of layers currently in the stack. */
    val size: Int
        get() {
            requireOpen()
            return layers.size
        }

    /** The index of the layer used as the draw target. */
    var targetIndex: Int = 0
        internal set

    /** The layer at [targetIndex]. */
    val target: Layer get() = get(targetIndex)

    /** The layer at [index]. */
    operator fun get(index: Int): Layer {
        requireOpen()
        return layers[index]
    }

    /** Appends a screen-sized layer and returns its index. */
    fun createLayer(): Int {
        requireOpen()
        layers.add(allocate(screenWidth, screenHeight))
        return layers.size - 1
    }

    /** Re-allocates every layer at a new screen size and marks them for upload. */
    internal fun resizeAll(
        width: Int,
        height: Int,
    ) {
        requireOpen()
        layers.forEach { it.replaceWith(allocate(width, height)) }
        screenWidth = width
        screenHeight = height
    }

    @KGESensitiveAPI
    override fun close() = layers.close()

    /** Fails fast once the stack is closed, before any access or allocation. */
    private fun requireOpen() {
        check(layers.size != 0) { "LayerStack is closed" }
    }

    private fun allocate(
        width: Int,
        height: Int,
    ): Layer =
        SpriteService
            .create(width, height, Pixmap.SampleMode.NORMAL, null)
            .letClosingIfFailed { sprite ->
                Decal(sprite, Decal.Filter.NEAREST, Decal.Wrap.CLAMP_TO_EDGE)
                    .letClosingIfFailed { decal -> Layer(sprite, decal) }
            }
}
