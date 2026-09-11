package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.BufferService
import dev.staticsanches.kge.buffer.copyInts
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.letClosingIfFailed

/**
 * Creates and duplicates [Sprite]s.
 *
 * Creation is an extension capability of the engine with a platform-
 * independent default: the surface storage goes through the current
 * [BufferService], so an allocator override covers surfaces too.
 * PNG loading/encoding is a separate capability, [PngService].
 */
interface SpriteService : KGEOverridable {
    /**
     * Creates a [width]x[height] surface, its storage allocated through the
     * current [BufferService]; content is unspecified until written.
     *
     * [name] is a diagnostic label: it shows up in [Sprite.toString] and in
     * the resource wrapper's messages. A non-positive dimension throws
     * [IllegalArgumentException] before any allocation.
     */
    fun create(
        width: Int,
        height: Int,
        sampleMode: Pixmap.SampleMode,
        name: String?,
    ): Sprite

    /**
     * Creates a detached copy of [sprite] — own storage, same pixels and
     * [sampleMode] — so mutating or closing one copy never touches the other.
     */
    fun duplicate(sprite: Sprite): Sprite

    companion object :
        KGEOverridable.Proxy<SpriteService>(SpriteService::class, spriteServiceDefault),
        SpriteService {
        override fun create(
            width: Int,
            height: Int,
            sampleMode: Pixmap.SampleMode,
            name: String?,
        ): Sprite = delegate.create(width, height, sampleMode, name)

        override fun duplicate(sprite: Sprite): Sprite = delegate.duplicate(sprite)
    }
}

/** Platform-independent default — allocation goes through [BufferService]. */
private val spriteServiceDefault: SpriteService =
    object : SpriteService {
        override fun create(
            width: Int,
            height: Int,
            sampleMode: Pixmap.SampleMode,
            name: String?,
        ): Sprite {
            require(width > 0 && height > 0) { "width and height must be positive: ${width}x$height" }
            // the buffer transfers to the Sprite; a failed construction must
            // not leak it (letClosingIfFailed — the resource guard)
            return BufferService
                .allocate(width * height * Int.SIZE_BYTES, name)
                .letClosingIfFailed { buffer -> Sprite(width, height, buffer, sampleMode, name) }
        }

        override fun duplicate(sprite: Sprite): Sprite =
            BufferService
                .allocate(sprite.width * sprite.height * Int.SIZE_BYTES, sprite.name)
                .letClosingIfFailed { buffer ->
                    buffer.resource.copyInts(0, sprite.buffer, 0, sprite.width * sprite.height)
                    Sprite(sprite.width, sprite.height, buffer, sprite.sampleMode, sprite.name)
                }
    }
