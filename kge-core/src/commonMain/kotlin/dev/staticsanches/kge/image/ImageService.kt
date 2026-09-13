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
 * The caller owns every payload handed to [load] or returned by [save]. Codec
 * work runs on [Dispatchers.Default], so a caller-supplied codec runs off the
 * caller's thread; a direct codec call bypassing [load]/[save] must dispatch
 * itself. A consumer may replace the whole behavior for the process via
 * [override][KGEOverridable.Proxy.override].
 */
interface ImageService : KGEOverridable {
    /**
     * A payload-to-sprite decoder: one call on [data] hands the decoded
     * `width`, `height` and row-major RGBA `pixels` to `consume`.
     *
     * The decoder allocates `pixels` (exactly `width * height * 4` bytes) and
     * owns it until it invokes `consume`; on any earlier path it must close it.
     * `consume` transfers ownership, after which the decoder must not close or
     * use `pixels` even if `consume` throws. [data] is caller-owned.
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
     * Decodes [data] with [decoder] into a [Sprite] with [sampleMode] and
     * [name], adopting the decoder's buffer (zero-copy). The decoder must call
     * `consume` exactly once with positive dimensions and an exactly
     * `width * height * 4`-byte buffer, else throws; the buffer is closed on
     * failure. [data] is caller-owned; runs on [Dispatchers.Default].
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
