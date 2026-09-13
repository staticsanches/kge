package dev.staticsanches.kge.image

import dev.staticsanches.kge.annotations.KGESensitiveAPI
import dev.staticsanches.kge.buffer.ByteBuffer
import dev.staticsanches.kge.buffer.fillInts
import dev.staticsanches.kge.buffer.formatBytes
import dev.staticsanches.kge.resource.KGEResource
import dev.staticsanches.kge.resource.ResourceWrapper
import dev.staticsanches.kge.resource.onCollectionObserved

/**
 * The concrete 2D surface: pixel `(x, y)` is the little-endian RGBA int at byte
 * offset `(y * width + x) * 4`. The sprite owns its [ResourceWrapper]: close
 * releases the native memory, and every access — including the out-of-bounds
 * paths — fails fast afterwards. Content is unspecified until written; an
 * unclosed sprite is reported on collection.
 *
 * A constructor rejection (dimensions, capacity) means ownership never
 * transferred: the caller keeps the wrapper and must close it. Creation and
 * duplication go through [SpriteService].
 */
@OptIn(KGESensitiveAPI::class)
class Sprite(
    override val width: Int,
    override val height: Int,
    private val storage: ResourceWrapper<ByteBuffer>,
    override var sampleMode: Pixmap.SampleMode = Pixmap.SampleMode.NORMAL,
    /** The surface's diagnostic label, shown by [toString] and the resource messages. */
    val name: String? = null,
) : Pixmap.Mutable,
    Pixmap.RawBacked,
    KGEResource by storage {
    init {
        require(width > 0 && height > 0) { "width and height must be positive: ${width}x$height" }
        require(storage.resource.capacity() == width * height * Int.SIZE_BYTES) {
            "buffer capacity must be exactly ${formatBytes(width * height * Int.SIZE_BYTES)}"
        }
    }

    /** Fails fast once released; the out-of-bounds branches do not touch the storage. */
    override fun get(
        x: Int,
        y: Int,
    ): Pixel {
        checkNotReleased()
        return super<Pixmap.Mutable>.get(x, y)
    }

    override fun set(
        x: Int,
        y: Int,
        pixel: Pixel,
    ): Boolean {
        checkNotReleased()
        return super.set(x, y, pixel)
    }

    private fun checkNotReleased() {
        if (storage.cleaned) {
            throw IllegalStateException("the sprite has been released and can not be used")
        }
    }

    override fun uncheckedGet(
        x: Int,
        y: Int,
    ): Pixel = Pixel.fromNativeRGBA(storage.resource.getInt(index(x, y) * Int.SIZE_BYTES))

    override fun uncheckedSet(
        x: Int,
        y: Int,
        pixel: Pixel,
    ) {
        storage.resource.putInt(index(x, y) * Int.SIZE_BYTES, pixel.nativeRGBA)
    }

    /** Hot path: fills the whole buffer without per-pixel checks. */
    override fun clear(pixel: Pixel) {
        storage.resource.fillInts(0, width * height, pixel.nativeRGBA)
    }

    /**
     * The sprite's raw storage, fails fast once the sprite is released. The
     * caller owns the bytes; writing outside the [Pixmap] contract (layout,
     * bounds, sample mode) is the caller's responsibility.
     */
    @KGESensitiveAPI
    override val buffer: ByteBuffer get() = storage.resource

    /** The sprite is contiguous: [width] int elements per row. */
    override val stride: Int get() = width

    /** The sprite's local `(0, 0)` is the start of [buffer]. */
    override val baseIndex: Int get() = 0

    /** Deterministic test seam: fires the platform collection trigger. */
    internal fun onCollectionObserved() = storage.onCollectionObserved()

    override fun toString(): String {
        val tag = "${width}x$height, $sampleMode"
        return name?.let { "Sprite($tag, \"$it\")" } ?: "Sprite($tag)"
    }
}
