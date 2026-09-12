package dev.staticsanches.kge.image

import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.overridable.KGEOverridable
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.letClosingIfFailed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads and saves [Sprite]s through caller-supplied codecs.
 *
 * The codec seam is an extension capability of the engine: [load] and [save]
 * are platform-independent orchestration, and the format/platform choice
 * lives in the [Decoder]/[Encoder] the caller passes. A consumer may replace
 * the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override]. The engine never owns a payload
 * handed to [load] or returned by [save] — the caller does.
 *
 * The codec work is dispatched on [Dispatchers.Default] so it runs off the
 * caller's thread, even for a caller-supplied codec that does not switch
 * dispatchers itself; a codec may still switch internally (e.g.
 * [Dispatchers.IO] for a blocking read). A direct codec call that bypasses
 * [load]/[save] is the caller's responsibility to dispatch.
 */
interface ImageService : KGEOverridable {
    /**
     * A payload-to-sprite decoder: one call on [data] hands the decoded
     * `width`, `height` and row-major RGBA pixels to `consume`.
     *
     * Ownership: the decoder allocates `pixels` — an engine buffer of exactly
     * `width * height * 4` bytes — and owns it until it invokes `consume`; on
     * every path before that call it must close it. Invoking
     * `consume(width, height, pixels)` transfers ownership to the consumer: the
     * decoder must not close or use `pixels` afterwards, whatever happens —
     * `consume` throwing included. [data] is caller-owned and is not closed by
     * the decoder.
     */
    fun interface Decoder<in T> {
        suspend fun decode(
            data: T,
            consume: (width: Int, height: Int, pixels: ResourceWrapper<ByteBuffer>) -> Unit,
        )
    }

    /**
     * A sprite-to-payload encoder. The returned payload passes to the caller,
     * which owns it — and closes it when it is a closable resource.
     */
    fun interface Encoder<out T> {
        suspend fun encode(sprite: Sprite): T
    }

    /**
     * Decodes [data] with [decoder] into a surface born with [sampleMode] and
     * [name] ([Sprite]'s diagnostic label). The decode runs on
     * [Dispatchers.Default].
     *
     * The decoder must call `consume` exactly once, with positive dimensions
     * and an engine buffer of exactly `width * height * 4` bytes; a second call,
     * a missing call, or a wrong-sized buffer throws. [load] **adopts** that
     * buffer as the returned [Sprite]'s storage (zero-copy), so the surface owns
     * it. When the [Sprite] cannot be constructed, or [decoder] fails after
     * `consume`, the buffer — and any already-created surface — is closed before
     * the failure propagates; a second `consume`'s extra buffer is closed too,
     * so nothing leaks. [data] is caller-owned and is never closed here.
     *
     * Adoption cannot go through `SpriteService.create` — the allocator would
     * allocate a second buffer and copy — so [load] constructs the [Sprite]
     * directly over the decoder's buffer.
     */
    suspend fun <T> load(
        data: T,
        decoder: Decoder<T>,
        sampleMode: Pixmap.SampleMode,
        name: String?,
    ): Sprite =
        withContext(Dispatchers.Default) {
            var sprite: Sprite? = null
            var consumed = false
            try {
                decoder.decode(data) { width, height, pixels ->
                    if (consumed) {
                        pixels.close()
                        error("the decoder consumed the data more than once")
                    }
                    consumed = true
                    sprite =
                        pixels.letClosingIfFailed { storage ->
                            val required = width * height * Int.SIZE_BYTES
                            require(storage.resource.capacity() == required) {
                                "the decoded buffer holds ${storage.resource.capacity()} bytes, " +
                                    "but ${width}x$height needs $required"
                            }
                            Sprite(width, height, storage, sampleMode, name)
                        }
                }
            } catch (e: Throwable) {
                sprite?.close()
                throw e
            }
            sprite ?: error("the decoder never consumed the data")
        }

    /**
     * Encodes [sprite] with [encoder], passing the payload through unchanged.
     * The encode runs on [Dispatchers.Default].
     */
    suspend fun <T> save(
        sprite: Sprite,
        encoder: Encoder<T>,
    ): T = withContext(Dispatchers.Default) { encoder.encode(sprite) }

    companion object :
        KGEOverridable.Proxy<ImageService>(ImageService::class, ImageServiceDefault),
        ImageService {
        override suspend fun <T> load(
            data: T,
            decoder: Decoder<T>,
            sampleMode: Pixmap.SampleMode,
            name: String?,
        ): Sprite = delegate.load(data, decoder, sampleMode, name)

        override suspend fun <T> save(
            sprite: Sprite,
            encoder: Encoder<T>,
        ): T = delegate.save(sprite, encoder)
    }
}

/** Platform-independent default: the caller's codec is the whole behavior. */
private object ImageServiceDefault : ImageService
